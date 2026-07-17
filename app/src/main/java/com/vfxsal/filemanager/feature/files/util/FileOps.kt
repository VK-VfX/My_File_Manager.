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

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "xml", "yaml", "yml", "ini", "conf", "cfg", "properties",
    "csv", "tsv", "log", "gradle", "kts", "kt", "java", "py", "js", "ts", "jsx", "tsx", "html",
    "htm", "css", "scss", "sh", "bash", "c", "h", "cpp", "hpp", "cs", "rs", "go", "rb", "php",
    "sql", "toml", "gitignore", "env", "bat", "ps1", "lua", "pl", "r", "swift", "dart", "vue",
)

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

    /** Same as [listChildren], but calls [onBatch] incrementally as entries are statted instead
     *  of blocking until the whole directory is read. A folder with thousands of entries can
     *  then show its first screenful right away instead of a blank loading state for the whole
     *  scan - folders smaller than [batchSize] behave exactly like [listChildren] (one batch). */
    fun listChildrenBatched(dir: File, batchSize: Int = 200, onBatch: (List<FileEntry>) -> Unit) {
        val children = dir.listFiles() ?: return
        var batch = ArrayList<FileEntry>(batchSize)
        for (child in children) {
            val entry = runCatching { FileEntry.from(child) }.getOrNull() ?: continue
            batch.add(entry)
            if (batch.size >= batchSize) {
                onBatch(batch)
                batch = ArrayList(batchSize)
            }
        }
        if (batch.isNotEmpty()) onBatch(batch)
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

    fun isTextEditable(entry: FileEntry): Boolean {
        if (entry.isDirectory) return false
        if (entry.extension.lowercase() in TEXT_EXTENSIONS) return true
        return mimeType(entry.file)?.startsWith("text/") == true
    }

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

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

    /**
     * Text-editable files open in the built-in editor instead of handing off to another app,
     * since viewing/editing files in place is the point of this app; everything else still
     * falls back to [tryOpen]. Returns false only when there's truly no app to open with.
     */
    fun openOrEdit(context: Context, entry: FileEntry, onEdit: (String) -> Unit): Boolean {
        if (isTextEditable(entry)) {
            onEdit(entry.path)
            return true
        }
        return tryOpen(context, entry.file)
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
