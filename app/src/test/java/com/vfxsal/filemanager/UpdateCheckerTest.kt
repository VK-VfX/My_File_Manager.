package com.vfxsal.filemanager

import com.vfxsal.filemanager.feature.update.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer patch version wins`() {
        assertTrue(UpdateChecker.isNewer("4.2.1", "4.2.0"))
    }

    @Test
    fun `newer minor version wins`() {
        assertTrue(UpdateChecker.isNewer("4.3.0", "4.2.9"))
    }

    @Test
    fun `newer major version wins`() {
        assertTrue(UpdateChecker.isNewer("5.0.0", "4.9.9"))
    }

    @Test
    fun `double digit segments compare numerically not lexically`() {
        assertTrue(UpdateChecker.isNewer("2.10.0", "2.9.0"))
        assertFalse(UpdateChecker.isNewer("2.9.0", "2.10.0"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(UpdateChecker.isNewer("4.2.0", "4.2.0"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(UpdateChecker.isNewer("4.1.0", "4.2.0"))
    }

    @Test
    fun `missing segments are treated as zero`() {
        assertTrue(UpdateChecker.isNewer("4.2.1", "4.2"))
        assertFalse(UpdateChecker.isNewer("4.2", "4.2.0"))
    }
}
