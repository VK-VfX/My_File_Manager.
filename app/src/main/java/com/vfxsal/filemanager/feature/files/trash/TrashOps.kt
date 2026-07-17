package com.vfxsal.filemanager.feature.files.trash

import android.content.Context
import com.vfxsal.filemanager.data.FileIndex
import com.vfxsal.filemanager.feature.clean.scan.FileTreeWalker
import com.vfxsal.filemanager.feature.files.tags.FileTagsStore
import com.vfxsal.filemanager.feature.settings.SettingsStore
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A soft-delete layer shared by every "delete" action in the app (Files browser/category
 * screens, and the Clean feature's junk/large-file/duplicate scanners). Trashed items are
 * physically moved into an app-private directory (invisible to other apps and the gallery)
 * rather than removed outright, and a flat manifest file records enough to restore them to
 * their original location later. Entries older than the user's retention setting (see
 * [SettingsStore.getTrashRetentionDays]) are permanently deleted the next time the trash
 * screen loads.
 */
object TrashOps {

    private const val TRASH_DIR_NAME = "trash"
    private const val MANIFEST_NAME = "manifest.txt"

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

    // Deliberately NOT context.filesDir: that's always internal storage, a different filesystem
    // from the shared/external storage nearly every user file lives on, so File.renameTo() would
    // always fail and silently fall back to a full byte-for-byte copy + delete - turning every
    // "delete" into reading and rewriting the entire file. getExternalFilesDir() is app-private
    // but lives on the *same* physical volume as the rest of primary shared storage, so moving a
    // file here is a genuine instant rename for the common case (a real SD card / secondary
    // volume is still a different filesystem and still needs a copy - that's an OS-level limit,
    // not something this app can avoid).
    fun trashDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, TRASH_DIR_NAME).apply { mkdirs() }

    private fun manifestFile(context: Context): File = File(trashDir(context), MANIFEST_NAME)

    fun moveToTrash(context: Context, file: File): Boolean = moveMultipleToTrash(context, listOf(file)) == 1

    /** Moves every file in [files] to trash, reading and writing the manifest exactly once for
     *  the whole batch instead of once per file - the per-file version made multi-select delete
     *  quadratic in the number of selected items, independent of how big any of them were. */
    fun moveMultipleToTrash(
        context: Context,
        files: List<File>,
        onProgress: (movedSoFar: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = synchronized(this) {
        if (files.isEmpty()) return 0
        val trashDir = trashDir(context)
        val newEntries = mutableListOf<TrashEntry>()
        val removedPaths = mutableListOf<String>()

        for ((index, file) in files.withIndex()) {
            if (file.exists()) {
                try {
                    val id = UUID.randomUUID().toString()
                    val originalPath = file.absolutePath
                    val isDirectory = file.isDirectory
                    val sizeBytes = if (isDirectory) FileTreeWalker.recursiveSize(file) else file.length()
                    val dest = File(trashDir, "$id-${file.name}")
                    val moved = file.renameTo(dest) || run {
                        file.copyRecursively(dest, overwrite = false)
                        file.deleteRecursively()
                    }
                    if (moved) {
                        newEntries.add(TrashEntry(id, originalPath, System.currentTimeMillis(), isDirectory, sizeBytes))
                        removedPaths.add(originalPath)
                    }
                } catch (e: Exception) {
                    // Skip this one, keep going with the rest of the batch.
                }
            }
            onProgress(index + 1, files.size)
        }

        if (newEntries.isNotEmpty()) {
            writeManifest(context, readManifest(context) + newEntries)
            FileTagsStore.onPathsRemoved(context, removedPaths)
            FileIndex.invalidate()
        }
        newEntries.size
    }

    /** Deletes every file in [files] outright, bypassing the recycle bin entirely - for when the
     *  user explicitly picks "Delete permanently" over the default, recoverable trash move. */
    fun deletePermanently(
        context: Context,
        files: List<File>,
        onProgress: (deletedSoFar: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        if (files.isEmpty()) return 0
        var deletedCount = 0
        val removedPaths = mutableListOf<String>()

        for ((index, file) in files.withIndex()) {
            try {
                if (file.deleteRecursively()) {
                    deletedCount++
                    removedPaths.add(file.absolutePath)
                }
            } catch (e: Exception) {
                // Skip this one, keep going with the rest of the batch.
            }
            onProgress(index + 1, files.size)
        }

        if (removedPaths.isNotEmpty()) {
            FileTagsStore.onPathsRemoved(context, removedPaths)
            FileIndex.invalidate()
        }
        return deletedCount
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
            if (restored) {
                removeManifestEntry(context, entry.id)
                FileIndex.invalidate()
            }
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
        val retentionMillis = TimeUnit.DAYS.toMillis(SettingsStore.getTrashRetentionDays(context).toLong())
        val cutoff = System.currentTimeMillis() - retentionMillis
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
    private fun removeManifestEntry(context: Context, id: String) {
        writeManifest(context, readManifest(context).filterNot { it.id == id })
    }
}
