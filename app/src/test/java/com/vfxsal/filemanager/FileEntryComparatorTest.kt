package com.vfxsal.filemanager

import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.browse.SortBy
import com.vfxsal.filemanager.feature.files.browse.buildFileEntryComparator
import org.junit.Assert.assertEquals
import org.junit.Test

class FileEntryComparatorTest {

    private fun entry(
        name: String,
        isDirectory: Boolean = false,
        sizeBytes: Long = 0L,
        lastModified: Long = 0L,
        category: FileCategory = FileCategory.OTHER,
    ) = FileEntry(
        path = "/storage/$name",
        name = name,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        category = if (isDirectory) FileCategory.FOLDER else category,
    )

    @Test
    fun `directories always sort before files`() {
        val entries = listOf(
            entry("b.txt"),
            entry("a-folder", isDirectory = true),
        )
        val sorted = entries.sortedWith(buildFileEntryComparator(SortBy.NAME, ascending = true))
        assertEquals("a-folder", sorted[0].name)
    }

    @Test
    fun `directories stay first even when sorting descending`() {
        val entries = listOf(
            entry("zzz.txt"),
            entry("folder", isDirectory = true),
        )
        val sorted = entries.sortedWith(buildFileEntryComparator(SortBy.NAME, ascending = false))
        assertEquals("folder", sorted[0].name)
    }

    @Test
    fun `name sort is case insensitive`() {
        val entries = listOf(entry("banana.txt"), entry("Apple.txt"))
        val sorted = entries.sortedWith(buildFileEntryComparator(SortBy.NAME, ascending = true))
        assertEquals("Apple.txt", sorted[0].name)
    }

    @Test
    fun `size sort descending puts biggest first`() {
        val entries = listOf(entry("small.txt", sizeBytes = 10), entry("big.txt", sizeBytes = 999))
        val sorted = entries.sortedWith(buildFileEntryComparator(SortBy.SIZE, ascending = false))
        assertEquals("big.txt", sorted[0].name)
    }

    @Test
    fun `date sort ascending puts oldest first`() {
        val entries = listOf(entry("new.txt", lastModified = 2000), entry("old.txt", lastModified = 1000))
        val sorted = entries.sortedWith(buildFileEntryComparator(SortBy.DATE, ascending = true))
        assertEquals("old.txt", sorted[0].name)
    }

    @Test
    fun `type sort groups by category then name`() {
        val entries = listOf(
            entry("song.mp3", category = FileCategory.AUDIO),
            entry("photo.jpg", category = FileCategory.IMAGES),
            entry("clip.mp4", category = FileCategory.VIDEOS),
        )
        val sorted = entries.sortedWith(buildFileEntryComparator(SortBy.TYPE, ascending = true))
        // FileCategory declaration order: FOLDER, IMAGES, VIDEOS, AUDIO, ...
        assertEquals(listOf("photo.jpg", "clip.mp4", "song.mp3"), sorted.map { it.name })
    }
}
