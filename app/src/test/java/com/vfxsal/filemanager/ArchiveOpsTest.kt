package com.vfxsal.filemanager

import com.vfxsal.filemanager.feature.files.util.ArchiveOps
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveOpsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `zip compress then extract round trips contents`() {
        val src = tempFolder.newFile("note.txt").apply { writeText("hello zip") }
        val out = tempFolder.newFolder("zipout")

        val archive = ArchiveOps.compress(listOf(src), out, "bundle", ArchiveOps.Format.ZIP)
        assertNotNull(archive)
        assertTrue(archive!!.name.endsWith(".zip"))

        val extracted = ArchiveOps.extract(archive)
        assertNotNull(extracted)
        assertEquals("hello zip", File(extracted, "note.txt").readText())
    }

    @Test
    fun `7z compress then extract round trips contents including a subfolder`() {
        val dir = tempFolder.newFolder("payload")
        File(dir, "a.txt").writeText("alpha")
        val nested = File(dir, "sub").apply { mkdirs() }
        File(nested, "b.txt").writeText("beta")
        val out = tempFolder.newFolder("sevenzout")

        val archive = ArchiveOps.compress(listOf(dir), out, "payload", ArchiveOps.Format.SEVEN_Z)
        assertNotNull(archive)
        assertTrue(archive!!.name.endsWith(".7z"))

        val extracted = ArchiveOps.extract(archive)
        assertNotNull(extracted)
        assertEquals("alpha", File(extracted, "payload/a.txt").readText())
        assertEquals("beta", File(extracted, "payload/sub/b.txt").readText())
    }

    @Test
    fun `compress with no sources returns null`() {
        val out = tempFolder.newFolder("empty")
        assertNull(ArchiveOps.compress(emptyList(), out, "x", ArchiveOps.Format.ZIP))
    }

    @Test
    fun `unsupported archive is not extractable`() {
        val rar = tempFolder.newFile("thing.rar")
        assertNull(ArchiveOps.extract(rar))
    }
}
