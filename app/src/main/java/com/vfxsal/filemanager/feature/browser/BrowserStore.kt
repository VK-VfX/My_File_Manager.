package com.vfxsal.filemanager.feature.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BrowserLink(val url: String, val title: String) {
    val host: String
        get() = url.substringAfter("://").substringBefore('/').removePrefix("www.")
}

/**
 * Lightweight bookmarks + recent-history store for the in-app browser, backed by
 * SharedPreferences as JSON. No database needed for a handful of links.
 */
object BrowserStore {

    private const val PREFS_NAME = "browser_store"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 40

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Bookmarks ----------------------------------------------------------------------

    fun bookmarks(context: Context): List<BrowserLink> = read(context, KEY_BOOKMARKS)

    fun isBookmarked(context: Context, url: String): Boolean = bookmarks(context).any { it.url == url }

    fun toggleBookmark(context: Context, url: String, title: String): Boolean {
        val current = bookmarks(context).toMutableList()
        val existing = current.indexOfFirst { it.url == url }
        val nowBookmarked: Boolean
        if (existing >= 0) {
            current.removeAt(existing)
            nowBookmarked = false
        } else {
            current.add(0, BrowserLink(url, title.ifBlank { url }))
            nowBookmarked = true
        }
        write(context, KEY_BOOKMARKS, current)
        return nowBookmarked
    }

    // --- History ------------------------------------------------------------------------

    fun history(context: Context): List<BrowserLink> = read(context, KEY_HISTORY)

    fun recordVisit(context: Context, url: String, title: String) {
        if (url.isBlank()) return
        val current = history(context).toMutableList()
        current.removeAll { it.url == url }
        current.add(0, BrowserLink(url, title.ifBlank { url }))
        while (current.size > MAX_HISTORY) current.removeAt(current.size - 1)
        write(context, KEY_HISTORY, current)
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }

    // --- JSON persistence ---------------------------------------------------------------

    private fun read(context: Context, key: String): List<BrowserLink> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                BrowserLink(obj.getString("url"), obj.optString("title"))
            }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, key: String, links: List<BrowserLink>) {
        val array = JSONArray()
        links.forEach { link ->
            array.put(JSONObject().put("url", link.url).put("title", link.title))
        }
        prefs(context).edit().putString(key, array.toString()).apply()
    }
}
