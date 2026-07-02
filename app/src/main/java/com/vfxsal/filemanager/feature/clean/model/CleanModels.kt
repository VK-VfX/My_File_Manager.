package com.vfxsal.filemanager.feature.clean.model

import com.vfxsal.filemanager.data.FileEntry
import java.io.File

enum class JunkCategory { APP_CACHE, JUNK_FILES, THUMBNAILS, EMPTY_FOLDERS, APK_INSTALLERS }

data class JunkItem(
    val path: String,
    val name: String,
    val category: JunkCategory,
    val sizeBytes: Long,
    val isDirectory: Boolean,
) {
    val file: File get() = File(path)
}

data class JunkGroup(val category: JunkCategory, val items: List<JunkItem>) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

enum class DuplicateScanPhase { SIZE_BUCKETING, HASHING }

data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    /** Sorted oldest-first; [files]\[0\] is the default keeper. */
    val files: List<FileEntry>,
) {
    val wastedBytes: Long get() = sizeBytes * (files.size - 1).coerceAtLeast(0)
}

data class LargeFileThreshold(val bytes: Long, val label: String)

val LARGE_FILE_THRESHOLDS = listOf(
    LargeFileThreshold(20L * 1024 * 1024, "20 MB"),
    LargeFileThreshold(50L * 1024 * 1024, "50 MB"),
    LargeFileThreshold(100L * 1024 * 1024, "100 MB"),
    LargeFileThreshold(200L * 1024 * 1024, "200 MB"),
)
