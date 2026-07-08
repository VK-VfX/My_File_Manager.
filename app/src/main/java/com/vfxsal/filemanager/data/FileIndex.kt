package com.vfxsal.filemanager.data

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A single shared snapshot of everything on external storage. Before this existed, the
 * Files home, every category screen, Timeline, the image viewer, and global search each
 * re-walked the entire storage tree on every visit - seconds of repeated I/O on devices
 * with large libraries. Now they all read one cached scan.
 *
 * Reads are stale-while-revalidate: once a snapshot exists, callers get it back instantly
 * even past its TTL, and an expired snapshot just kicks off one background rescan - so
 * screens never block on a walk after the first one in the process's lifetime. The cache
 * is dropped explicitly (via [invalidate]) whenever the app itself mutates storage, so
 * screens refresh immediately after the user changes something; changes made by *other*
 * apps are picked up by the TTL-triggered background rescan.
 *
 * [allEntries] only blocks (on Dispatchers.IO) when the cache is completely cold.
 */
object FileIndex {

    private const val TTL_MILLIS = 5 * 60_000L

    private val mutex = Mutex()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached: List<FileEntry>? = null

    @Volatile
    private var scannedAtMillis = 0L

    @Volatile
    private var refreshing = false

    /** Every file and directory under external storage (excluding the root itself). */
    suspend fun allEntries(forceRefresh: Boolean = false): List<FileEntry> {
        val existing = cached
        if (existing != null && !forceRefresh) {
            if (System.currentTimeMillis() - scannedAtMillis >= TTL_MILLIS) {
                triggerBackgroundRefresh()
            }
            return existing
        }
        return mutex.withLock {
            val current = cached
            if (current != null && !forceRefresh) {
                current
            } else {
                val scanned = scan()
                cached = scanned
                scannedAtMillis = System.currentTimeMillis()
                scanned
            }
        }
    }

    /** Files only - what most consumers (categories, timeline, viewer) want. */
    suspend fun allFiles(forceRefresh: Boolean = false): List<FileEntry> =
        allEntries(forceRefresh).filter { !it.isDirectory }

    /** Drop the snapshot after any operation that changed storage contents. */
    fun invalidate() {
        cached = null
    }

    private fun triggerBackgroundRefresh() {
        if (refreshing) return
        refreshing = true
        refreshScope.launch {
            try {
                val scanned = scan()
                cached = scanned
                scannedAtMillis = System.currentTimeMillis()
            } finally {
                refreshing = false
            }
        }
    }

    private fun scan(): List<FileEntry> {
        val root = Environment.getExternalStorageDirectory()
        return root.walkTopDown()
            .filter { it != root }
            .mapNotNull { runCatching { FileEntry.from(it) }.getOrNull() }
            .toList()
    }
}
