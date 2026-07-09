package com.vfxsal.filemanager

import com.vfxsal.filemanager.feature.browser.MediaKind
import com.vfxsal.filemanager.feature.browser.MediaSniffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSnifferTest {

    @Test
    fun `classifies video extensions`() {
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://cdn.example.com/clip.mp4"))
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("http://host/a/b/movie.webm"))
    }

    @Test
    fun `classifies audio and file extensions`() {
        assertEquals(MediaKind.AUDIO, MediaSniffer.classify("https://host/song.mp3"))
        assertEquals(MediaKind.FILE, MediaSniffer.classify("https://host/manual.pdf"))
    }

    @Test
    fun `ignores query strings and fragments when reading the extension`() {
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://host/v.mp4?token=abc&x=1"))
        assertEquals(MediaKind.AUDIO, MediaSniffer.classify("https://host/a.m4a#t=30"))
    }

    @Test
    fun `classifies HLS and DASH manifests as streams`() {
        assertEquals(MediaKind.STREAM, MediaSniffer.classify("https://host/live/index.m3u8"))
        assertEquals(MediaKind.STREAM, MediaSniffer.classify("https://host/dash/manifest.mpd?x=1"))
    }

    @Test
    fun `classifies additional video and audio extensions`() {
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://host/clip.flv"))
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://host/clip.mpeg"))
        assertEquals(MediaKind.AUDIO, MediaSniffer.classify("https://host/track.flac"))
    }

    @Test
    fun `rejects non-http, extensionless and unknown urls`() {
        assertNull(MediaSniffer.classify(null))
        assertNull(MediaSniffer.classify("blob:https://host/uuid"))
        assertNull(MediaSniffer.classify("https://host/page"))
        assertNull(MediaSniffer.classify("https://host/style.css"))
    }
}
