package com.vfxsal.filemanager.feature.clean.scan

import android.os.Environment
import com.vfxsal.filemanager.data.FileCategory

data class DashboardScanResult(
    val categoryTotals: Map<FileCategory, Long>,
    val junkTeaserCount: Int,
    val junkTeaserBytes: Long,
    val largeTeaserCount: Int,
    val largeTeaserBytes: Long,
    val duplicateTeaserCount: Int,
    val duplicateTeaserBytes: Long,
)

/**
 * Single full pass over external storage that powers the dashboard's category donut
 * plus cheap teasers for the three detail screens. The junk/large teasers reuse the
 * same rules as [JunkScanner]/[LargeFileScanner] (extension list, 50MB default) but
 * skip the extra app-cache lookup and directory-walk niceties those screens do, so
 * the numbers here are a close approximation refined on first navigation into a
 * detail screen. The duplicate teaser is the cheap phase-one, size-bucket-only
 * estimate (no hashing) - true duplicate detection happens in [DuplicateScanner].
 */
object DashboardScanner {
    private val JUNK_EXTENSIONS = setOf("tmp", "log", "bak", "old", "dmp")
    private val THUMBNAIL_DIR_NAMES = setOf("thumbnails", ".thumbnails")
    const val DEFAULT_LARGE_FILE_THRESHOLD = 50L * 1024 * 1024

    suspend fun scan(onProgress: suspend (Int) -> Unit = {}): DashboardScanResult {
        val root = Environment.getExternalStorageDirectory()
        val categoryTotals = mutableMapOf<FileCategory, Long>()
        val sizeBucketCounts = mutableMapOf<Long, Int>()
        var junkCount = 0
        var junkBytes = 0L
        var largeCount = 0
        var largeBytes = 0L
        var scanned = 0

        FileTreeWalker.walk(
            root = root,
            skipSubtree = { dir ->
                FileTreeWalker.isAndroidDataOrObb(dir) || dir.name.lowercase() in THUMBNAIL_DIR_NAMES
            },
            onFile = { file ->
                scanned++
                if (scanned % 200 == 0) onProgress(scanned)
                val size = file.length()
                val category = FileCategory.fromExtension(file.extension)
                categoryTotals[category] = (categoryTotals[category] ?: 0L) + size

                if (file.extension.lowercase() in JUNK_EXTENSIONS) {
                    junkCount++
                    junkBytes += size
                }
                if (size >= DEFAULT_LARGE_FILE_THRESHOLD) {
                    largeCount++
                    largeBytes += size
                }
                if (size > 0) {
                    sizeBucketCounts[size] = (sizeBucketCounts[size] ?: 0) + 1
                }
            },
        )
        onProgress(scanned)

        var duplicateCount = 0
        var duplicateBytes = 0L
        for ((size, count) in sizeBucketCounts) {
            if (count >= 2) {
                duplicateCount += count
                duplicateBytes += size * count
            }
        }

        return DashboardScanResult(
            categoryTotals = categoryTotals,
            junkTeaserCount = junkCount,
            junkTeaserBytes = junkBytes,
            largeTeaserCount = largeCount,
            largeTeaserBytes = largeBytes,
            duplicateTeaserCount = duplicateCount,
            duplicateTeaserBytes = duplicateBytes,
        )
    }
}
