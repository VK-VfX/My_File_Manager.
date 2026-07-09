package com.vfxsal.filemanager.feature.browser

import android.webkit.MimeTypeMap

enum class MediaKind(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    STREAM("Stream"),
    FILE("File"),
}

data class DetectedMedia(val url: String, val kind: MediaKind) {
    val fileName: String
        get() = url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank { "media" }
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

    private fun extensionOf(url: String): String =
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

    /**
     * Collects candidate media URLs from the loaded page as a JSON array: `<video>/<audio>/
     * <source>` current/`src`/`data-src`, Open Graph & Twitter player `<meta>` tags, and any
     * `<a href>` that points at a known media/stream/file extension. [classify] filters the
     * result down to things actually worth offering.
     */
    const val COLLECT_PAGE_MEDIA_JS = """
        (function() {
            var out = [];
            function add(u) { if (u && out.indexOf(u) === -1) out.push(u); }
            var media = document.querySelectorAll('video, audio, source');
            for (var i = 0; i < media.length; i++) {
                add(media[i].currentSrc || media[i].src);
                add(media[i].getAttribute('data-src'));
                add(media[i].getAttribute('data-source'));
            }
            var metas = document.querySelectorAll(
                'meta[property="og:video"], meta[property="og:video:url"], ' +
                'meta[property="og:video:secure_url"], meta[property="og:audio"], ' +
                'meta[name="twitter:player:stream"]'
            );
            for (var j = 0; j < metas.length; j++) { add(metas[j].getAttribute('content')); }
            var links = document.querySelectorAll('a[href]');
            var re = /\.(mp4|webm|mkv|mov|m4v|avi|flv|ogv|wmv|mpe?g|mp3|m4a|aac|flac|wav|ogg|opus|m3u8|mpd|ts|zip|rar|7z|apk|pdf|epub|docx?|xlsx?|pptx?)(\?|#|${'$'})/i;
            for (var k = 0; k < links.length; k++) {
                var h = links[k].href;
                if (h && re.test(h)) add(h);
            }
            return JSON.stringify(out);
        })()
    """
}
