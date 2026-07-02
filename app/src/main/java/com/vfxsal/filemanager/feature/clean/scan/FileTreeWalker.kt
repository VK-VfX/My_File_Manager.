package com.vfxsal.filemanager.feature.clean.scan

import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Depth-first, non-recursive walker over a directory tree.
 *
 * On Android 11+ (API 30+), MANAGE_EXTERNAL_STORAGE still does not grant access to
 * other apps' `Android/data/*` and `Android/obb/*` directories - that's an OS-level
 * carve-out. Listing them throws SecurityException or silently returns nothing, so
 * every caller must treat those subtrees as opaque rather than trying to "fix" access
 * with more permissions. [isAndroidDataOrObb] is the default [skipSubtree] predicate;
 * callers that want to also show their own app's cache must read it separately via
 * `context.cacheDir` / `context.externalCacheDirs`, not through this walker.
 *
 * The walker checks for coroutine cancellation between every directory listing so a
 * scan launched from a Compose screen stops promptly once the screen (and its
 * ViewModel scope) goes away, and it never recurses so pathological directory depth
 * can't blow the call stack.
 */
object FileTreeWalker {

    fun isAndroidDataOrObb(dir: File): Boolean {
        val parent = dir.parentFile ?: return false
        return parent.name == "Android" && (dir.name == "data" || dir.name == "obb")
    }

    suspend fun walk(
        root: File,
        skipSubtree: (File) -> Boolean = ::isAndroidDataOrObb,
        onFile: suspend (File) -> Unit = {},
        onDirectory: suspend (dir: File, children: List<File>) -> Unit = { _, _ -> },
        onSkippedDirectory: suspend (File) -> Unit = {},
    ) {
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            if (!dir.canRead()) continue
            val children = try {
                dir.listFiles()?.toList()
            } catch (e: SecurityException) {
                null
            } ?: continue

            onDirectory(dir, children)

            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (skipSubtree(child)) {
                        onSkippedDirectory(child)
                    } else {
                        stack.addLast(child)
                    }
                } else {
                    onFile(child)
                }
            }
        }
    }

    /** Non-suspend recursive size helper for small, already-known-readable subtrees (own cache dirs, a thumbnails folder). */
    fun recursiveSize(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        var total = 0L
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try {
                dir.listFiles()
            } catch (e: SecurityException) {
                null
            } ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }
}
