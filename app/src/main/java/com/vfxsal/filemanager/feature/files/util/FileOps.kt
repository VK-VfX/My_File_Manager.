package com.vfxsal.filemanager.feature.files.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import java.io.File

/** Must match the authority declared for the FileProvider in AndroidManifest.xml. */
const val FILE_PROVIDER_AUTHORITY = "com.vfxsal.filemanager.fileprovider"

/**
 * All filesystem side effects for the Files feature live here so view models stay
 * thin. Every function is a plain blocking call - callers are expected to invoke
 * these from a coroutine already dispatched on Dispatchers.IO.
 */
object FileOps {

    fun listChildren(dir: File): List<FileEntry> {
        val children = dir.listFiles() ?: return emptyList()
        return children.mapNotNull { runCatching { FileEntry.from(it) }.getOrNull() }
    }

    /** Every descendant (files and folders) beneath [root], excluding [root] itself. */
    fun scanRecursive(root: File): List<FileEntry> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it != root }
            .mapNotNull { runCatching { FileEntry.from(it) }.getOrNull() }
            .toList()
    }

    fun filesByCategory(root: File, category: FileCategory): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        root.walkTopDown().forEach { file ->
            if (file.isFile) {
                val entry = runCatching { FileEntry.from(file) }.getOrNull()
                if (entry != null && entry.category == category) result.add(entry)
            }
        }
        return result
    }

    fun mostRecentFiles(root: File, limit: Int): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        root.walkTopDown().forEach { file ->
            if (file.isFile) {
                runCatching { FileEntry.from(file) }.getOrNull()?.let { result.add(it) }
            }
        }
        return result.sortedByDescending { it.lastModified }.take(limit)
    }

    /** Appends " (1)", " (2)", ... before the extension until [targetDir] has no clash. */
    fun uniqueDestination(targetDir: File, name: String): File {
        var candidate = File(targetDir, name)
        if (!candidate.exists()) return candidate
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var index = 1
        do {
            candidate = File(targetDir, "$base ($index)$ext")
            index++
        } while (candidate.exists())
        return candidate
    }

    fun copyInto(source: File, targetDir: File): File {
        val dest = uniqueDestination(targetDir, source.name)
        source.copyRecursively(dest, overwrite = false)
        return dest
    }

    /** Same-volume rename first (instant), falling back to copy + delete across volumes. */
    fun moveInto(source: File, targetDir: File): File {
        val dest = uniqueDestination(targetDir, source.name)
        if (source.renameTo(dest)) return dest
        source.copyRecursively(dest, overwrite = false)
        source.deleteRecursively()
        return dest
    }

    fun delete(file: File): Boolean = file.deleteRecursively()

    fun mimeType(file: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)

    fun viewIntent(context: Context, file: File): Intent {
        val uri = contentUri(context, file)
        val mime = mimeType(file) ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareIntent(context: Context, files: List<File>): Intent {
        val uris = ArrayList(files.map { contentUri(context, it) })
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType(files.first()) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /** Returns false (instead of throwing) when no app can handle the view intent. */
    fun tryOpen(context: Context, file: File): Boolean = try {
        context.startActivity(viewIntent(context, file))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun tryShare(context: Context, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        return try {
            val chooser = Intent.createChooser(shareIntent(context, files), null).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
