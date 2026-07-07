package com.vfxsal.filemanager.feature.clean.scan

import android.os.Environment
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.clean.model.DuplicateGroup
import com.vfxsal.filemanager.feature.clean.model.DuplicateScanPhase
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Two-phase duplicate detection: first bucket every file by exact size (cheap, no I/O
 * beyond a stat), then only for buckets with 2+ files stream a SHA-256 hash to confirm
 * true content equality. Files that never share a size with anything else are skipped
 * entirely in phase two.
 */
object DuplicateScanner {
    private const val HASH_BUFFER_SIZE = 8192

    suspend fun scan(
        onProgress: suspend (phase: DuplicateScanPhase, count: Int) -> Unit = { _, _ -> },
    ): List<DuplicateGroup> {
        val root = Environment.getExternalStorageDirectory()
        val bySize = mutableMapOf<Long, MutableList<File>>()
        var scanned = 0

        FileTreeWalker.walk(
            root = root,
            onFile = { file ->
                val size = file.length()
                if (size > 0) {
                    bySize.getOrPut(size) { mutableListOf() } += file
                }
                scanned++
                if (scanned % 200 == 0) onProgress(DuplicateScanPhase.SIZE_BUCKETING, scanned)
            },
        )
        onProgress(DuplicateScanPhase.SIZE_BUCKETING, scanned)

        val candidates = bySize.values.filter { it.size >= 2 }.flatten()
        val byHash = mutableMapOf<String, MutableList<File>>()
        var hashed = 0

        for (file in candidates) {
            coroutineContext.ensureActive()
            val hash = try {
                hashFile(file)
            } catch (e: IOException) {
                null
            } ?: continue
            byHash.getOrPut(hash) { mutableListOf() } += file
            hashed++
            if (hashed % 20 == 0) onProgress(DuplicateScanPhase.HASHING, hashed)
        }
        onProgress(DuplicateScanPhase.HASHING, hashed)

        return byHash.entries
            .filter { it.value.size >= 2 }
            .map { (hash, files) ->
                val sorted = files.sortedBy { it.lastModified() }
                DuplicateGroup(
                    hash = hash,
                    sizeBytes = sorted.first().length(),
                    files = sorted.map { FileEntry.from(it) },
                )
            }
            .sortedByDescending { it.wastedBytes }
    }

    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
