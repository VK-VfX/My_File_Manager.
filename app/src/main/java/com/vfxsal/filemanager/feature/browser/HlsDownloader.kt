package com.vfxsal.filemanager.feature.browser

import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** An `#EXT-X-KEY` tag as parsed from playlist text, before its key bytes have been fetched over
 *  the network - kept separate from [HlsDownloader]'s resolved key so the parsing itself stays a
 *  pure, network-free function that can be unit tested directly. */
internal data class HlsKeyTag(val method: String, val keyUri: String?, val ivHex: String?)

internal data class HlsSegment(val url: String, val keyTag: HlsKeyTag?)

/** Pure HLS playlist-text parsing (no network I/O), split out of [HlsDownloader] so it's directly
 *  unit testable. */
internal object HlsPlaylistParser {

    fun isMasterPlaylist(text: String): Boolean = text.contains("#EXT-X-STREAM-INF")

    /** From a master playlist's text, picks the highest-`BANDWIDTH` variant and resolves it
     *  against [baseUri]. Returns null if the text has no `#EXT-X-STREAM-INF` variants. */
    fun selectBestVariant(masterPlaylistText: String, baseUri: URI): String? {
        val lines = masterPlaylistText.lines()
        var bestBandwidth = -1L
        var bestUri: String? = null
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val variantUri = lines.getOrNull(i + 1)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("#") }
            if (variantUri != null && bandwidth > bestBandwidth) {
                bestBandwidth = bandwidth
                bestUri = baseUri.resolve(variantUri).toString()
            }
        }
        return bestUri
    }

    /** Parses a media playlist's segment URLs, tagging each with whatever `#EXT-X-KEY` was active
     *  when it appeared. Key bytes aren't fetched here - only [HlsDownloader] touches the network. */
    fun parseSegments(mediaPlaylistText: String, baseUri: URI): List<HlsSegment> {
        val segments = mutableListOf<HlsSegment>()
        var currentKey: HlsKeyTag? = null
        for (rawLine in mediaPlaylistText.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-KEY") -> currentKey = parseKeyTag(line)
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> segments.add(HlsSegment(baseUri.resolve(line).toString(), currentKey))
            }
        }
        return segments
    }

    fun mediaSequenceStart(mediaPlaylistText: String): Long =
        Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)").find(mediaPlaylistText)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /** Modern (fMP4/CMAF) HLS streams share one `#EXT-X-MAP` initialization segment across all
     *  media segments instead of each segment being a standalone MPEG-TS chunk - without
     *  prepending it, the fragments have no track/codec info and won't play. */
    fun initSegmentUri(mediaPlaylistText: String, baseUri: URI): String? {
        val line = mediaPlaylistText.lines().map { it.trim() }.firstOrNull { it.startsWith("#EXT-X-MAP") }
            ?: return null
        val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return null
        return baseUri.resolve(uri).toString()
    }

    /** Per the HLS spec: when a key's IV attribute is omitted, it's the segment's media sequence
     *  number as a big-endian 16-byte value. */
    fun ivFromSequence(sequence: Long): ByteArray = ByteArray(16).also { arr ->
        for (i in 0 until 8) arr[15 - i] = ((sequence shr (i * 8)) and 0xFF).toByte()
    }

    fun ivFromHex(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun parseKeyTag(line: String): HlsKeyTag? {
        val method = Regex("METHOD=([^,]+)").find(line)?.groupValues?.get(1) ?: return null
        if (method == "NONE") return null
        val keyUri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
        val ivHex = Regex("IV=0[xX]([0-9A-Fa-f]+)").find(line)?.groupValues?.get(1)
        return HlsKeyTag(method, keyUri, ivHex)
    }
}

/**
 * Downloads and reassembles an HLS (`.m3u8`) stream into a single playable `.mp4` - something the
 * system [android.app.DownloadManager] can't do on its own, since it only fetches one URL and a
 * stream is a playlist of many. Handles master playlists (picks the highest-bandwidth variant),
 * fMP4/CMAF init segments, AES-128 segment encryption, bounded concurrency, and per-segment
 * retries. Segments encrypted with anything other than AES-128 (SAMPLE-AES, DRM) fail loudly
 * rather than writing an unplayable file silently. The raw merged stream is repackaged into a
 * standalone `.mp4` via [MediaRemuxer] - without this, a downloaded stream is a `.ts`/`.m4s` blob
 * this app's own Video tab doesn't recognize and most external players and gallery apps handle
 * poorly, if at all. If remuxing fails (an unsupported codec, or a genuinely broken capture), the
 * raw merged file is kept as a `.ts` fallback rather than losing the download entirely.
 */
class HlsDownloader(private val userAgent: String?) {

    data class Progress(val completed: Int, val total: Int)

    private data class SegmentKey(val keyBytes: ByteArray, val iv: ByteArray)
    private data class ResolvedSegment(val url: String, val key: SegmentKey?)
    private data class ResolvedPlaylist(val initSegmentUrl: String?, val segments: List<ResolvedSegment>)

    // Real players give up around 6-8 simultaneous connections to one host anyway - going wider
    // just trips CDN rate limits without downloading any faster.
    private val segmentDispatcher = Dispatchers.IO.limitedParallelism(6)

    /** Downloads [manifestUrl] into `<outputDir>/<baseFileName>.mp4` (or `.ts` if remuxing to mp4
     *  isn't possible for this stream), returning the file actually written. */
    suspend fun download(
        manifestUrl: String,
        outputDir: File,
        baseFileName: String,
        onProgress: (Progress) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val playlist = resolvePlaylist(manifestUrl)
            check(playlist.segments.isNotEmpty()) { "No segments found in playlist" }

            val workDir = File(outputDir, ".hls_download_${System.nanoTime()}").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                val total = playlist.segments.size
                val completed = AtomicInteger(0)
                onProgress(Progress(0, total))

                val initFile = playlist.initSegmentUrl?.let { uri ->
                    File(workDir, "init.bin").also { it.writeBytes(fetchBytes(uri)) }
                }

                val chunkFiles = coroutineScope {
                    playlist.segments.mapIndexed { index, segment ->
                        async(segmentDispatcher) {
                            val chunkFile = File(workDir, "segment_%06d.bin".format(index))
                            downloadSegmentWithRetry(segment, chunkFile)
                            onProgress(Progress(completed.incrementAndGet(), total))
                            chunkFile
                        }
                    }.awaitAll()
                }

                val mergedFile = File(workDir, "merged.raw")
                FileOutputStream(mergedFile).use { out ->
                    initFile?.takeIf { it.exists() }?.inputStream()?.use { it.copyTo(out) }
                    for (chunk in chunkFiles) {
                        if (chunk.exists()) chunk.inputStream().use { it.copyTo(out) }
                    }
                }

                outputDir.mkdirs()
                val mp4Target = uniqueFile(outputDir, baseFileName, "mp4")
                try {
                    MediaRemuxer.remuxToMp4(mergedFile, mp4Target)
                    mp4Target
                } catch (e: Exception) {
                    mp4Target.delete()
                    val tsTarget = uniqueFile(outputDir, baseFileName, "ts")
                    mergedFile.copyTo(tsTarget, overwrite = true)
                    tsTarget
                }
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        var candidate = File(dir, "$baseName.$extension")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($suffix).$extension")
            suffix++
        }
        return candidate
    }

    // --- Playlist resolution (network + parsing) --------------------------------------------

    private fun resolvePlaylist(manifestUrl: String, depth: Int = 0): ResolvedPlaylist {
        check(depth < 4) { "Playlist redirected through too many master/variant hops" }
        val text = fetchText(manifestUrl)
        val baseUri = URI(manifestUrl)

        if (HlsPlaylistParser.isMasterPlaylist(text)) {
            val variant = HlsPlaylistParser.selectBestVariant(text, baseUri)
                ?: error("Master playlist had no playable variants")
            return resolvePlaylist(variant, depth + 1)
        }

        val sequenceStart = HlsPlaylistParser.mediaSequenceStart(text)
        val parsed = HlsPlaylistParser.parseSegments(text, baseUri)
        val initSegmentUrl = HlsPlaylistParser.initSegmentUri(text, baseUri)
        val keyBytesCache = mutableMapOf<String, ByteArray>()

        val segments = parsed.mapIndexed { index, segment ->
            val key = segment.keyTag?.let { tag ->
                resolveKey(tag, baseUri, sequenceStart + index, keyBytesCache)
            }
            ResolvedSegment(segment.url, key)
        }
        return ResolvedPlaylist(initSegmentUrl, segments)
    }

    private fun resolveKey(
        tag: HlsKeyTag,
        baseUri: URI,
        sequence: Long,
        keyBytesCache: MutableMap<String, ByteArray>,
    ): SegmentKey {
        // SAMPLE-AES and DRM schemes need a decryptor we don't have - fail loudly instead of
        // writing an unplayable file that looks like it downloaded fine.
        check(tag.method == "AES-128") { "Encrypted stream (method=${tag.method}) is not supported" }
        val keyUri = tag.keyUri ?: error("AES-128 key tag is missing a URI")
        val resolvedKeyUri = baseUri.resolve(keyUri).toString()
        val keyBytes = keyBytesCache.getOrPut(resolvedKeyUri) { fetchBytes(resolvedKeyUri) }
        val iv = tag.ivHex?.let { HlsPlaylistParser.ivFromHex(it) } ?: HlsPlaylistParser.ivFromSequence(sequence)
        return SegmentKey(keyBytes, iv)
    }

    // --- Segment fetching ------------------------------------------------------------------

    private suspend fun downloadSegmentWithRetry(segment: ResolvedSegment, outputFile: File, attempts: Int = 3) {
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                val bytes = fetchBytes(segment.url)
                val plain = segment.key?.let { decrypt(bytes, it) } ?: bytes
                outputFile.writeBytes(plain)
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < attempts - 1) delay(500L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Failed to download segment: ${segment.url}")
    }

    private fun decrypt(bytes: ByteArray, key: SegmentKey): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.keyBytes, "AES"), IvParameterSpec(key.iv))
        return cipher.doFinal(bytes)
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
            if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
        }

    private fun fetchText(url: String): String =
        openConnection(url).let { it.inputStream.bufferedReader().use { r -> r.readText() } }

    private fun fetchBytes(url: String): ByteArray =
        openConnection(url).let { it.inputStream.use { s -> s.readBytes() } }
}
