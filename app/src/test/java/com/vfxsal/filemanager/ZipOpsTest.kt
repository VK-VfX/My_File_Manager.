package com.vfxsal.filemanager

import com.vfxsal.filemanager.feature.files.util.ZipOps
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipOpsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `zip then unzip round trips file contents`() {
        val source = tempFolder.newFile("hello.txt").apply { writeText("hello world") }
        val zipFile = File(tempFolder.root, "archive.zip")
        val extractDir = tempFolder.newFolder("extracted")

        assertTrue(ZipOps.zip(listOf(source), zipFile))
        assertTrue(ZipOps.unzip(zipFile, extractDir))
        assertEquals("hello world", File(extractDir, "hello.txt").readText())
    }

    @Test
    fun `zip includes directory contents recursively`() {
        val dir = tempFolder.newFolder("photos")
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")
        val zipFile = File(tempFolder.root, "dir.zip")
        val extractDir = tempFolder.newFolder("out")

        assertTrue(ZipOps.zip(listOf(dir), zipFile))
        assertTrue(ZipOps.unzip(zipFile, extractDir))
        assertEquals("a", File(extractDir, "photos/a.txt").readText())
        assertEquals("b", File(extractDir, "photos/b.txt").readText())
    }

    @Test
    fun `unzip rejects zip slip entries that escape the target directory`() {
        val evilZip = File(tempFolder.root, "evil.zip")
        ZipOutputStream(FileOutputStream(evilZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../escaped.txt"))
            zos.write("gotcha".toByteArray())
            zos.closeEntry()
        }
        val extractDir = tempFolder.newFolder("safe")

        assertFalse(ZipOps.unzip(evilZip, extractDir))
        assertFalse(File(tempFolder.root, "escaped.txt").exists())
    }
}
