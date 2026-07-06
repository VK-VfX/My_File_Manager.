package com.vfxsal.filemanager

import com.vfxsal.filemanager.feature.files.util.BatchRenameOps
import com.vfxsal.filemanager.feature.files.util.RenamePattern
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BatchRenameOpsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createFiles(vararg names: String): List<File> =
        names.map { tempFolder.newFile(it) }

    @Test
    fun `sequential rename numbers files in list order and keeps extensions`() {
        val files = createFiles("one.jpg", "two.jpg", "three.jpg")

        val renamed = BatchRenameOps.rename(files, "Holiday", RenamePattern.SEQUENTIAL)

        assertEquals(3, renamed)
        assertTrue(File(tempFolder.root, "Holiday (1).jpg").exists())
        assertTrue(File(tempFolder.root, "Holiday (2).jpg").exists())
        assertTrue(File(tempFolder.root, "Holiday (3).jpg").exists())
    }

    @Test
    fun `file without extension gets no trailing dot`() {
        val files = createFiles("README")

        BatchRenameOps.rename(files, "Notes", RenamePattern.SEQUENTIAL)

        assertTrue(File(tempFolder.root, "Notes (1)").exists())
    }

    @Test
    fun `name collision falls back to a unique destination instead of overwriting`() {
        tempFolder.newFile("Pic (1).jpg")
        val files = createFiles("original.jpg")

        val renamed = BatchRenameOps.rename(files, "Pic", RenamePattern.SEQUENTIAL)

        assertEquals(1, renamed)
        assertTrue(File(tempFolder.root, "Pic (1).jpg").exists())
        assertTrue(File(tempFolder.root, "Pic (1) (1).jpg").exists())
    }

    @Test
    fun `onRenamed reports old and new paths for every success`() {
        val files = createFiles("a.txt", "b.txt")
        val moves = mutableListOf<Pair<String, String>>()

        BatchRenameOps.rename(files, "Doc", RenamePattern.SEQUENTIAL) { old, new ->
            moves.add(old to new)
        }

        assertEquals(2, moves.size)
        assertTrue(moves[0].first.endsWith("a.txt"))
        assertTrue(moves[0].second.endsWith("Doc (1).txt"))
    }
}
