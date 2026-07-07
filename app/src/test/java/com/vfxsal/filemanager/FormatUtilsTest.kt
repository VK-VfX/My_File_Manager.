package com.vfxsal.filemanager

import com.vfxsal.filemanager.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun `zero and negative bytes render as zero`() {
        assertEquals("0 B", FormatUtils.formatFileSize(0))
        assertEquals("0 B", FormatUtils.formatFileSize(-5))
    }

    @Test
    fun `sub kilobyte counts stay in bytes`() {
        assertEquals("512 B", FormatUtils.formatFileSize(512))
    }

    @Test
    fun `unit scales with magnitude`() {
        assertTrue(FormatUtils.formatFileSize(2_048).endsWith("KB"))
        assertTrue(FormatUtils.formatFileSize(5L * 1024 * 1024).endsWith("MB"))
        assertTrue(FormatUtils.formatFileSize(3L * 1024 * 1024 * 1024).endsWith("GB"))
    }

    @Test
    fun `duration formats minutes and hours`() {
        assertEquals("0:05", FormatUtils.formatDuration(5_000))
        assertEquals("2:03", FormatUtils.formatDuration(123_000))
        assertEquals("1:01:05", FormatUtils.formatDuration(3_665_000))
    }
}
