package com.vfxsal.filemanager

import com.vfxsal.filemanager.feature.browser.HlsPlaylistParser
import java.net.URI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    private val base = URI("https://cdn.example.com/stream/master.m3u8")

    @Test
    fun `detects master playlists by their stream-inf tag`() {
        assertTrue(HlsPlaylistParser.isMasterPlaylist("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8"))
        assertTrue(!HlsPlaylistParser.isMasterPlaylist("#EXTM3U\n#EXTINF:4,\nseg0.ts"))
    }

    @Test
    fun `selects the highest bandwidth variant and resolves it against the base uri`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            360p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            720p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480
            480p/index.m3u8
        """.trimIndent()

        val best = HlsPlaylistParser.selectBestVariant(master, base)

        assertEquals("https://cdn.example.com/stream/720p/index.m3u8", best)
    }

    @Test
    fun `returns null when a master playlist has no variants`() {
        assertNull(HlsPlaylistParser.selectBestVariant("#EXTM3U\n#EXT-X-VERSION:3", base))
    }

    @Test
    fun `parses segment urls in order and resolves relative paths`() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:4.0,
            seg0.ts
            #EXTINF:4.0,
            seg1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val segments = HlsPlaylistParser.parseSegments(playlist, base)

        assertEquals(
            listOf(
                "https://cdn.example.com/stream/seg0.ts",
                "https://cdn.example.com/stream/seg1.ts",
            ),
            segments.map { it.url },
        )
        assertTrue(segments.all { it.keyTag == null })
    }

    @Test
    fun `tags segments with the active encryption key`() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x00000000000000000000000000000001
            seg0.ts
            #EXT-X-KEY:METHOD=NONE
            seg1.ts
        """.trimIndent()

        val segments = HlsPlaylistParser.parseSegments(playlist, base)

        assertEquals("AES-128", segments[0].keyTag?.method)
        assertEquals("key.bin", segments[0].keyTag?.keyUri)
        assertNull(segments[1].keyTag)
    }

    @Test
    fun `reads the media sequence start, defaulting to zero`() {
        assertEquals(42L, HlsPlaylistParser.mediaSequenceStart("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:42"))
        assertEquals(0L, HlsPlaylistParser.mediaSequenceStart("#EXTM3U\n#EXTINF:4,"))
    }

    @Test
    fun `derives the iv from a sequence number as big-endian bytes`() {
        val iv = HlsPlaylistParser.ivFromSequence(1L)
        val expected = ByteArray(16).also { it[15] = 1 }
        assertArrayEquals(expected, iv)

        val iv256 = HlsPlaylistParser.ivFromSequence(256L)
        val expected256 = ByteArray(16).also { it[14] = 1 }
        assertArrayEquals(expected256, iv256)
    }

    @Test
    fun `decodes an explicit hex iv`() {
        val iv = HlsPlaylistParser.ivFromHex("000102030405060708090a0b0c0d0e0f")
        assertArrayEquals(ByteArray(16) { it.toByte() }, iv)
    }
}
