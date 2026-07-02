package com.vfxsal.filemanager.feature.clean.scan

import android.content.Context
import android.os.Environment
import com.vfxsal.filemanager.feature.clean.model.JunkCategory
import com.vfxsal.filemanager.feature.clean.model.JunkGroup
import com.vfxsal.filemanager.feature.clean.model.JunkItem
import java.io.File

object JunkScanner {
    private val JUNK_EXTENSIONS = setOf("tmp", "log", "bak", "old", "dmp")
    private val THUMBNAIL_DIR_NAMES = setOf("thumbnails", ".thumbnails")

    @Suppress("DEPRECATION")
    suspend fun scan(context: Context, onProgress: suspend (Int) -> Unit = {}): List<JunkGroup> {
        val root = Environment.getExternalStorageDirectory()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val junkFiles = mutableListOf<JunkItem>()
        val thumbnailDirs = mutableListOf<JunkItem>()
        val emptyDirs = mutableListOf<JunkItem>()
        val apkInstallers = mutableListOf<JunkItem>()
        var scanned = 0

        FileTreeWalker.walk(
            root = root,
            skipSubtree = { dir ->
                FileTreeWalker.isAndroidDataOrObb(dir) || dir.name.lowercase() in THUMBNAIL_DIR_NAMES
            },
            onFile = { file ->
                scanned++
                if (scanned % 50 == 0) onProgress(scanned)
                val ext = file.extension.lowercase()
                when {
                    ext in JUNK_EXTENSIONS -> junkFiles += file.toJunkItem(JunkCategory.JUNK_FILES)
                    ext == "apk" && file.parentFile?.absolutePath == downloadsDir?.absolutePath ->
                        apkInstallers += file.toJunkItem(JunkCategory.APK_INSTALLERS)
                }
            },
            onDirectory = { dir, children ->
                if (children.isEmpty() && dir.absolutePath != root.absolutePath) {
                    emptyDirs += dir.toJunkItem(JunkCategory.EMPTY_FOLDERS)
                }
            },
            onSkippedDirectory = { dir ->
                if (dir.name.lowercase() in THUMBNAIL_DIR_NAMES) {
                    thumbnailDirs += dir.toJunkItem(JunkCategory.THUMBNAILS, FileTreeWalker.recursiveSize(dir))
                }
            },
        )
        onProgress(scanned)

        val appCache = collectAppCacheItems(context)

        return listOf(
            JunkGroup(JunkCategory.APP_CACHE, appCache),
            JunkGroup(JunkCategory.JUNK_FILES, junkFiles),
            JunkGroup(JunkCategory.THUMBNAILS, thumbnailDirs),
            JunkGroup(JunkCategory.EMPTY_FOLDERS, emptyDirs),
            JunkGroup(JunkCategory.APK_INSTALLERS, apkInstallers),
        ).filter { it.items.isNotEmpty() }
    }

    private fun collectAppCacheItems(context: Context): List<JunkItem> {
        val dirs = buildList {
            context.cacheDir?.let(::add)
            context.externalCacheDirs?.filterNotNull()?.let(::addAll)
        }.distinctBy { it.absolutePath }
        return dirs.filter { it.exists() }.map { it.toJunkItem(JunkCategory.APP_CACHE) }
    }

    private fun File.toJunkItem(category: JunkCategory, sizeOverride: Long? = null): JunkItem = JunkItem(
        path = absolutePath,
        name = name,
        category = category,
        sizeBytes = sizeOverride ?: if (isDirectory) FileTreeWalker.recursiveSize(this) else length(),
        isDirectory = isDirectory,
    )
}
