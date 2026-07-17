package com.vfxsal.filemanager.feature.browser

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Repackages a raw MPEG-TS (or fMP4/CMAF) file into a standalone `.mp4` container without
 * re-encoding - just copying already-compressed samples from [MediaExtractor] into [MediaMuxer].
 * This is what makes a downloaded HLS stream show up as a normal video everywhere: this app's own
 * Video tab only recognizes mp4/mkv/webm/etc. (not raw `.ts`), and external players, gallery apps,
 * and share targets are far more likely to handle `.mp4` correctly than a bare transport stream.
 *
 * Pure Android SDK (`android.media`) - no `ffmpeg-kit` or similar dependency, which would add tens
 * of megabytes of native libraries per ABI for a capability the platform already provides for the
 * common case (repackaging, not transcoding).
 */
object MediaRemuxer {

    fun remuxToMp4(input: File, output: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val trackIndexMap = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackIndexMap[i] = muxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }
            check(trackIndexMap.isNotEmpty()) { "No video/audio tracks found to remux" }

            muxer.start()
            val buffer = ByteBuffer.allocate(1 shl 20)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val dstTrack = trackIndexMap[extractor.sampleTrackIndex]
                if (dstTrack != null) {
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                }
                extractor.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            runCatching { extractor.release() }
        }
    }
}
