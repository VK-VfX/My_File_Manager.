package com.vfxsal.filemanager.feature.browser

import android.webkit.MimeTypeMap

enum class MediaKind(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    FILE("File"),
}

data class DetectedMedia(val url: String, val kind: MediaKind) {
    val fileName: String
        get() = url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank { "media" }
}

/**
 * Spots downloadable media as a page loads. Two sources feed it: every network request the
 * WebView makes (which catches videos the moment they start buffering) and a post-load DOM
 * scan of `<video>/<audio>/<source>` elements (which catches media that hasn't started
 * loading yet). Blob and DRM/segmented streams can't be saved by DownloadManager, so only
 * plain http(s) URLs with a recognizable file extension are reported.
 */
object MediaSniffer {

    private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "avi", "3gp", "m4v")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    private val fileExtensions = setOf("pdf", "zip", "rar", "7z", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub")

    /** Returns what kind of downloadable media [url] points at, or null if it isn't one. */
    fun classify(url: String?): MediaKind? {
        if (url == null) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val extension = url
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase()
        return when {
            extension in videoExtensions -> MediaKind.VIDEO
            extension in audioExtensions -> MediaKind.AUDIO
            extension in fileExtensions -> MediaKind.FILE
            else -> null
        }
    }

    fun mimeTypeFor(url: String): String? {
        val extension = url
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "")
            .lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    /** Collects the sources of every video/audio element on the page as a JSON array. */
    const val COLLECT_PAGE_MEDIA_JS = """
        (function() {
            var out = [];
            var els = document.querySelectorAll('video, audio, source');
            for (var i = 0; i < els.length; i++) {
                var src = els[i].currentSrc || els[i].src;
                if (src) out.push(src);
            }
            return JSON.stringify(out);
        })()
    """
}
