package com.vfxsal.filemanager.feature.clean.scan

import android.os.Environment
import com.vfxsal.filemanager.data.FileEntry

object LargeFileScanner {
    suspend fun scan(minSizeBytes: Long, onProgress: suspend (Int) -> Unit = {}): List<FileEntry> {
        val root = Environment.getExternalStorageDirectory()
        val results = mutableListOf<FileEntry>()
        var scanned = 0

        FileTreeWalker.walk(
            root = root,
            onFile = { file ->
                scanned++
                if (scanned % 100 == 0) onProgress(scanned)
                val size = file.length()
                if (size >= minSizeBytes) {
                    results += FileEntry.from(file)
                }
            },
        )
        onProgress(scanned)
        return results.sortedByDescending { it.sizeBytes }
    }
}
