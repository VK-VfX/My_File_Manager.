package com.vfxsal.filemanager.feature.browser

import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

enum class MediaKind(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    STREAM("Stream"),
    FILE("File"),
}

/** [sizeBytes]/[isProbingSize] are filled in lazily (see [DownloadOps.probeSize]) once the media
 *  sheet asks for them - most pages never need a size lookup, so it isn't done eagerly for every
 *  hit the sniffer finds. */
data class DetectedMedia(val url: String, val kind: MediaKind) {
    val fileName: String
        get() = url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank { "media" }

    val format: String
        get() = MediaSniffer.extensionOf(url).uppercase().ifBlank { kind.label }

    var sizeBytes: Long? by mutableStateOf(null)
    var isProbingSize: Boolean by mutableStateOf(false)
}

/**
 * Spots downloadable media as a page loads. Three sources feed it: every network request the
 * WebView makes (which catches videos the moment they start buffering), a post-load DOM scan of
 * `<video>/<audio>/<source>` elements plus `<meta>` player tags and media `<a href>` links
 * (which catches media that hasn't started loading yet), and long-pressed images. Blob and DRM
 * streams can't be saved, and HLS/DASH manifests are only playlists (their segments still need
 * assembling), so those are flagged as [MediaKind.STREAM] rather than promised as direct files.
 */
object MediaSniffer {

    private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "avi", "3gp", "m4v", "flv", "ogv", "mpg", "mpeg", "wmv", "ts")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "weba")
    private val streamExtensions = setOf("m3u8", "mpd", "m4s")
    private val fileExtensions = setOf("pdf", "zip", "rar", "7z", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub")

    internal fun extensionOf(url: String): String =
        url.substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase()

    /** Returns what kind of downloadable media [url] points at, or null if it isn't one. */
    fun classify(url: String?): MediaKind? {
        if (url == null) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return when (extensionOf(url)) {
            in streamExtensions -> MediaKind.STREAM
            in videoExtensions -> MediaKind.VIDEO
            in audioExtensions -> MediaKind.AUDIO
            in fileExtensions -> MediaKind.FILE
            else -> null
        }
    }

    fun mimeTypeFor(url: String): String? = when (extensionOf(url)) {
        "m3u8" -> "application/vnd.apple.mpegurl"
        "mpd" -> "application/dash+xml"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(url))
    }

    /** A candidate media URL found by [COLLECT_PAGE_MEDIA_JS]. [assertedKind] is set only for
     *  `<video>`/`<audio>` elements and their `<meta>` equivalents, where the markup itself
     *  confirms the kind - useful because video hosts/CDNs routinely serve the actual file from
     *  a signed, extension-less URL that [classify] alone would have no way to recognize. */
    data class PageMediaHit(val url: String, val assertedKind: MediaKind?)

    fun parsePageMedia(json: String): List<PageMediaHit> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val url = obj.optString("u").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = when (obj.optString("k")) {
                "video" -> MediaKind.VIDEO
                "audio" -> MediaKind.AUDIO
                else -> null
            }
            PageMediaHit(url, kind)
        }
    }.getOrDefault(emptyList())

    /**
     * Collects candidate media from the loaded page as a JSON array of `{u, k}` objects:
     * `<video>/<audio>/<source>` current/`src`/`data-src` and Open Graph/Twitter player `<meta>`
     * tags carry an explicit `k` ("video"/"audio") since the element type already tells us the
     * kind; plain `<a href>` links matching a known media/stream/file extension carry no `k` and
     * fall back to [classify]. [parsePageMedia] turns the JSON back into [PageMediaHit]s.
     */
    const val COLLECT_PAGE_MEDIA_JS = """
        (function() {
            var out = [];
            var seen = {};
            function add(u, k) {
                if (!u || seen[u]) return;
                seen[u] = true;
                out.push(k ? {u: u, k: k} : {u: u});
            }
            var media = document.querySelectorAll('video, audio, source');
            for (var i = 0; i < media.length; i++) {
                var el = media[i];
                var kind = el.tagName === 'AUDIO' || el.parentElement && el.parentElement.tagName === 'AUDIO'
                    ? 'audio' : 'video';
                add(el.currentSrc || el.src, kind);
                add(el.getAttribute('data-src'), kind);
                add(el.getAttribute('data-source'), kind);
            }
            var videoMetas = document.querySelectorAll(
                'meta[property="og:video"], meta[property="og:video:url"], ' +
                'meta[property="og:video:secure_url"], meta[name="twitter:player:stream"]'
            );
            for (var j = 0; j < videoMetas.length; j++) { add(videoMetas[j].getAttribute('content'), 'video'); }
            var audioMetas = document.querySelectorAll('meta[property="og:audio"]');
            for (var m = 0; m < audioMetas.length; m++) { add(audioMetas[m].getAttribute('content'), 'audio'); }
            var links = document.querySelectorAll('a[href]');
            var re = /\.(mp4|webm|mkv|mov|m4v|avi|flv|ogv|wmv|mpe?g|mp3|m4a|aac|flac|wav|ogg|opus|m3u8|mpd|ts|zip|rar|7z|apk|pdf|epub|docx?|xlsx?|pptx?)(\?|#|${'$'})/i;
            for (var k = 0; k < links.length; k++) {
                var h = links[k].href;
                if (h && re.test(h)) add(h, null);
            }
            return JSON.stringify(out);
        })()
    """
}
