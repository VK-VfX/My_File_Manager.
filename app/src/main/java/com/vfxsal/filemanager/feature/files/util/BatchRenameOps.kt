package com.vfxsal.filemanager.feature.files.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RenamePattern { SEQUENTIAL, DATE_MODIFIED }

object BatchRenameOps {

    /**
     * Renames [files] in place using [baseName] plus a numeric or date suffix, preserving each
     * file's extension. [files] should already be in the order the user wants numbered/dated -
     * callers pass the currently displayed (sorted) order. Returns how many were renamed.
     * [onRenamed] fires with the old and new absolute paths for each success, so callers can
     * carry along path-keyed metadata like color tags.
     */
    fun rename(
        files: List<File>,
        baseName: String,
        pattern: RenamePattern,
        onRenamed: (oldPath: String, newPath: String) -> Unit = { _, _ -> },
    ): Int {
        var renamed = 0
        files.forEachIndexed { index, file ->
            val parent = file.parentFile ?: return@forEachIndexed
            val ext = file.extension
            val suffix = when (pattern) {
                RenamePattern.SEQUENTIAL -> " (${index + 1})"
                RenamePattern.DATE_MODIFIED -> {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(file.lastModified()))
                    "_$stamp"
                }
            }
            val newName = if (ext.isNotEmpty()) "$baseName$suffix.$ext" else "$baseName$suffix"
            val dest = FileOps.uniqueDestination(parent, newName)
            if (file.renameTo(dest)) {
                renamed++
                onRenamed(file.absolutePath, dest.absolutePath)
            }
        }
        return renamed
    }
}
