package com.vfxsal.filemanager.feature.clean.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.clean.model.SimilarPhotoGroup
import com.vfxsal.filemanager.feature.files.util.FileOps
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Finds near-duplicate photos (burst shots, re-saved copies, screenshots taken twice) that
 * the exact-hash duplicate finder can't catch, using a perceptual difference-hash (dHash):
 * each photo is shrunk to a 9x8 grayscale grid and hashed to 64 bits by comparing each pixel
 * to its right neighbor, so photos that look alike hash to a similar bit pattern even after
 * re-compression or a light crop. Comparing every photo against every other is O(n^2), so the
 * scan is capped at the most recent [MAX_IMAGES_SCANNED] photos to keep it fast on-device.
 */
object SimilarPhotosScanner {

    private const val HASH_SIZE = 8
    private const val SIMILARITY_THRESHOLD = 10
    private const val MAX_IMAGES_SCANNED = 1500

    suspend fun scan(root: File, onProgress: suspend (Int) -> Unit = {}): List<SimilarPhotoGroup> {
        val images = FileOps.filesByCategory(root, FileCategory.IMAGES)
            .sortedByDescending { it.lastModified }
            .take(MAX_IMAGES_SCANNED)

        val hashes = mutableListOf<Pair<FileEntry, Long>>()
        images.forEachIndexed { index, entry ->
            coroutineContext.ensureActive()
            val hash = runCatching { computeDHash(entry.file) }.getOrNull()
            if (hash != null) hashes.add(entry to hash)
            if (index % 25 == 0) onProgress(index)
        }
        onProgress(hashes.size)

        val visited = BooleanArray(hashes.size)
        val groups = mutableListOf<SimilarPhotoGroup>()
        for (i in hashes.indices) {
            coroutineContext.ensureActive()
            if (visited[i]) continue
            val cluster = mutableListOf(hashes[i].first)
            visited[i] = true
            for (j in i + 1 until hashes.size) {
                if (visited[j]) continue
                if (hammingDistance(hashes[i].second, hashes[j].second) <= SIMILARITY_THRESHOLD) {
                    cluster.add(hashes[j].first)
                    visited[j] = true
                }
            }
            if (cluster.size >= 2) {
                groups.add(SimilarPhotoGroup(cluster.sortedByDescending { it.lastModified }))
            }
        }
        return groups.sortedByDescending { it.totalBytes }
    }

    private fun computeDHash(file: File): Long {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val source = BitmapFactory.decodeFile(file.absolutePath, options) ?: return 0L
        val scaled = Bitmap.createScaledBitmap(source, HASH_SIZE + 1, HASH_SIZE, true)
        if (scaled !== source) source.recycle()
        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_SIZE) {
            for (x in 0 until HASH_SIZE) {
                val left = grayscale(scaled.getPixel(x, y))
                val right = grayscale(scaled.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        scaled.recycle()
        return hash
    }

    private fun grayscale(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
