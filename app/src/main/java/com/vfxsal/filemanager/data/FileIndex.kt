package com.vfxsal.filemanager.data

import android.os.Environment
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A single shared snapshot of everything on external storage. Before this existed, the
 * Files home, every category screen, Timeline, the image viewer, and global search each
 * re-walked the entire storage tree on every visit - seconds of repeated I/O on devices
 * with large libraries. Now they all read one cached scan.
 *
 * The cache lives for [TTL_MILLIS] and is dropped explicitly (via [invalidate]) whenever
 * the app itself mutates storage - delete, rename, move, paste, vault, new folder - so
 * screens refresh immediately after the user changes something, while repeated visits
 * within the window are instant. Changes made by *other* apps are picked up when the TTL
 * lapses.
 *
 * [allEntries] blocks on a full scan when the cache is cold; call it from Dispatchers.IO.
 */
object FileIndex {

    private const val TTL_MILLIS = 60_000L

    private val mutex = Mutex()

    @Volatile
    private var cached: List<FileEntry>? = null

    @Volatile
    private var scannedAtMillis = 0L

    /** Every file and directory under external storage (excluding the root itself). */
    suspend fun allEntries(forceRefresh: Boolean = false): List<FileEntry> = mutex.withLock {
        val existing = cached
        val isFresh = System.currentTimeMillis() - scannedAtMillis < TTL_MILLIS
        if (existing != null && isFresh && !forceRefresh) {
            existing
        } else {
            val scanned = scan()
            cached = scanned
            scannedAtMillis = System.currentTimeMillis()
            scanned
        }
    }

    /** Files only - what most consumers (categories, timeline, viewer) want. */
    suspend fun allFiles(forceRefresh: Boolean = false): List<FileEntry> =
        allEntries(forceRefresh).filter { !it.isDirectory }

    /** Drop the snapshot after any operation that changed storage contents. */
    fun invalidate() {
        cached = null
    }

    private fun scan(): List<FileEntry> {
        val root = Environment.getExternalStorageDirectory()
        return root.walkTopDown()
            .filter { it != root }
            .mapNotNull { runCatching { FileEntry.from(it) }.getOrNull() }
            .toList()
    }
}
