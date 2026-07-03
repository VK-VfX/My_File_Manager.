package com.vfxsal.filemanager.feature.files.trash

import android.content.Context
import com.vfxsal.filemanager.feature.clean.scan.FileTreeWalker
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A soft-delete layer shared by every "delete" action in the app (Files browser/category
 * screens, and the Clean feature's junk/large-file/duplicate scanners). Trashed items are
 * physically moved into an app-private directory (invisible to other apps and the gallery)
 * rather than removed outright, and a flat manifest file records enough to restore them to
 * their original location later. Entries older than [PURGE_AFTER_MILLIS] are permanently
 * deleted the next time the trash screen loads.
 */
object TrashOps {

    private const val TRASH_DIR_NAME = "trash"
    private const val MANIFEST_NAME = "manifest.txt"
    private val PURGE_AFTER_MILLIS = TimeUnit.DAYS.toMillis(30)

    data class TrashEntry(
        val id: String,
        val originalPath: String,
        val trashedAtMillis: Long,
        val isDirectory: Boolean,
        val sizeBytes: Long,
    ) {
        fun trashedFile(context: Context): File =
            File(trashDir(context), "$id-${File(originalPath).name}")
    }

    fun trashDir(context: Context): File = File(context.filesDir, TRASH_DIR_NAME).apply { mkdirs() }

    private fun manifestFile(context: Context): File = File(trashDir(context), MANIFEST_NAME)

    fun moveToTrash(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val id = UUID.randomUUID().toString()
            val originalPath = file.absolutePath
            val isDirectory = file.isDirectory
            val sizeBytes = if (isDirectory) FileTreeWalker.recursiveSize(file) else file.length()
            val dest = File(trashDir(context), "$id-${file.name}")
            val moved = file.renameTo(dest) || run {
                file.copyRecursively(dest, overwrite = false)
                file.deleteRecursively()
            }
            if (moved) {
                appendManifestEntry(context, TrashEntry(id, originalPath, System.currentTimeMillis(), isDirectory, sizeBytes))
            }
            moved
        } catch (e: Exception) {
            false
        }
    }

    fun listEntries(context: Context): List<TrashEntry> =
        readManifest(context).filter { it.trashedFile(context).exists() }

    fun restore(context: Context, entry: TrashEntry): Boolean {
        val source = entry.trashedFile(context)
        if (!source.exists()) {
            removeManifestEntry(context, entry.id)
            return false
        }
        val originalFile = File(entry.originalPath)
        val destParent = originalFile.parentFile ?: return false
        return try {
            destParent.mkdirs()
            val dest = if (originalFile.exists()) uniqueName(destParent, originalFile.name) else originalFile
            val restored = source.renameTo(dest) || run {
                source.copyRecursively(dest, overwrite = false)
                source.deleteRecursively()
            }
            if (restored) removeManifestEntry(context, entry.id)
            restored
        } catch (e: Exception) {
            false
        }
    }

    fun deleteForever(context: Context, entry: TrashEntry): Boolean {
        val file = entry.trashedFile(context)
        val deleted = !file.exists() || file.deleteRecursively()
        if (deleted) removeManifestEntry(context, entry.id)
        return deleted
    }

    fun emptyTrash(context: Context): Int =
        listEntries(context).count { deleteForever(context, it) }

    fun purgeExpired(context: Context) {
        val cutoff = System.currentTimeMillis() - PURGE_AFTER_MILLIS
        listEntries(context).filter { it.trashedAtMillis < cutoff }.forEach { deleteForever(context, it) }
    }

    private fun uniqueName(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        do {
            candidate = File(dir, "$base ($index)$ext")
            index++
        } while (candidate.exists())
        return candidate
    }

    private fun readManifest(context: Context): List<TrashEntry> {
        val file = manifestFile(context)
        if (!file.exists()) return emptyList()
        val lines = file.readLines()
        val entries = mutableListOf<TrashEntry>()
        var i = 0
        while (i + 4 < lines.size) {
            entries.add(
                TrashEntry(
                    id = lines[i],
                    originalPath = lines[i + 1],
                    trashedAtMillis = lines[i + 2].toLongOrNull() ?: 0L,
                    isDirectory = lines[i + 3].toBoolean(),
                    sizeBytes = lines[i + 4].toLongOrNull() ?: 0L,
                ),
            )
            i += 5
        }
        return entries
    }

    private fun writeManifest(context: Context, entries: List<TrashEntry>) {
        val sb = StringBuilder()
        entries.forEach { entry ->
            sb.append(entry.id).append('\n')
            sb.append(entry.originalPath).append('\n')
            sb.append(entry.trashedAtMillis).append('\n')
            sb.append(entry.isDirectory).append('\n')
            sb.append(entry.sizeBytes).append('\n')
        }
        manifestFile(context).writeText(sb.toString())
    }

    @Synchronized
    private fun appendManifestEntry(context: Context, entry: TrashEntry) {
        writeManifest(context, readManifest(context) + entry)
    }

    @Synchronized
    private fun removeManifestEntry(context: Context, id: String) {
        writeManifest(context, readManifest(context).filterNot { it.id == id })
    }
}
