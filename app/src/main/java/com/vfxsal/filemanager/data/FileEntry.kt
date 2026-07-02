package com.vfxsal.filemanager.data

import java.io.File

/**
 * A single filesystem entry as shown by the Files and Clean features.
 * Kept as a plain data class (rather than wrapping [File] directly) so the
 * rest of the app doesn't need to touch java.io.File or re-run stat calls.
 */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: FileCategory,
) {
    val file: File get() = File(path)
    val extension: String get() = name.substringAfterLast('.', "")

    companion object {
        fun from(file: File): FileEntry {
            val isDir = file.isDirectory
            return FileEntry(
                path = file.absolutePath,
                name = file.name,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                category = if (isDir) FileCategory.FOLDER else FileCategory.fromExtension(file.extension),
            )
        }
    }
}
