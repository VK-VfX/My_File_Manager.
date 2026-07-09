package com.vfxsal.filemanager

import com.vfxsal.filemanager.feature.browser.normalizeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeInputTest {

    @Test
    fun `full urls pass through untouched`() {
        assertEquals("https://example.com", normalizeInput("https://example.com"))
        assertEquals("http://example.com/x", normalizeInput("  http://example.com/x  "))
    }

    @Test
    fun `bare domains get an https prefix`() {
        assertEquals("https://example.com", normalizeInput("example.com"))
        assertEquals("https://sub.example.co.uk/path", normalizeInput("sub.example.co.uk/path"))
    }

    @Test
    fun `plain text becomes a web search`() {
        val result = normalizeInput("cute cat videos")
        assertTrue(result.startsWith("https://www.google.com/search?q="))
        assertTrue(result.contains("cute"))
    }

    @Test
    fun `spaced input with a dot is still treated as a search`() {
        val result = normalizeInput("what is 3.5")
        assertTrue(result.startsWith("https://www.google.com/search?q="))
    }
}
