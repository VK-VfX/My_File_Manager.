package com.vfxsal.filemanager.feature.files.tags

import android.content.Context
import androidx.compose.ui.graphics.Color

/** Color labels a user can stick on any file/folder as an organizing scheme independent
 *  of where the item actually lives on disk. */
enum class FileTag(val label: String, val color: Color) {
    RED("Red", Color(0xFFE53935)),
    ORANGE("Orange", Color(0xFFFB8C00)),
    YELLOW("Yellow", Color(0xFFFDD835)),
    GREEN("Green", Color(0xFF43A047)),
    BLUE("Blue", Color(0xFF1E88E5)),
    PURPLE("Purple", Color(0xFF8E24AA)),
}

/** Keyed by absolute file path since tags are a lightweight per-path label, not something
 *  that needs to survive a rename/move. */
object FileTagsStore {

    private const val PREFS_NAME = "file_tags"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTag(context: Context, path: String): FileTag? {
        val name = prefs(context).getString(path, null) ?: return null
        return runCatching { FileTag.valueOf(name) }.getOrNull()
    }

    fun setTag(context: Context, path: String, tag: FileTag?) {
        setTag(context, listOf(path), tag)
    }

    fun setTag(context: Context, paths: Collection<String>, tag: FileTag?) {
        prefs(context).edit().apply {
            paths.forEach { path -> if (tag == null) remove(path) else putString(path, tag.name) }
        }.apply()
    }
}
