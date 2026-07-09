package com.vfxsal.filemanager.feature.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import java.net.HttpURLConnection
import java.net.URL

/**
 * Download engine built on the system [DownloadManager] rather than hand-rolled HTTP:
 * downloads survive the app being killed, pause/resume over flaky networks, and post
 * their own status notifications for free. This object just enqueues requests and keeps
 * the list of IDs this app started (in SharedPreferences) so the in-app Downloads screen
 * can show and manage exactly our downloads.
 */
object DownloadOps {

    private const val PREFS_NAME = "browser_downloads"
    private const val KEY_IDS = "download_ids"
    private const val URL_PREFS_NAME = "browser_download_urls"

    data class DownloadInfo(
        val id: Long,
        val title: String,
        val status: Int,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val localUri: String?,
        val mediaType: String?,
    ) {
        val isRunning: Boolean
            get() = status == DownloadManager.STATUS_RUNNING ||
                status == DownloadManager.STATUS_PENDING ||
                status == DownloadManager.STATUS_PAUSED
        val isSuccessful: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
        val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
        val progressFraction: Float
            get() = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun urlPrefs(context: Context) = context.getSharedPreferences(URL_PREFS_NAME, Context.MODE_PRIVATE)

    private fun downloadManager(context: Context): DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Enqueues a download into the public Downloads folder. Returns the id, or null on failure. */
    fun enqueue(
        context: Context,
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
    ): Long? = runCatching {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            // Sites often gate media behind a session; forward the WebView's cookies so
            // downloads started from a logged-in page actually work.
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        }
        val id = downloadManager(context).enqueue(request)
        rememberId(context, id)
        // Remember the source URL so a failed download can be retried.
        urlPrefs(context).edit().putString(id.toString(), url).apply()
        id
    }.getOrNull()

    /** Probes [url]'s size with a HEAD request (falling back to no result rather than downloading
     *  the body) so the browser can show "12.4 MB" before the user commits to a download. Blocking -
     *  callers must run this off the main thread. Many CDNs omit Content-Length or reject HEAD for
     *  streamed/segmented media, so a null result is normal and just means "size unknown". */
    fun probeSize(url: String, userAgent: String?): Long? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            connectTimeout = 6_000
            readTimeout = 6_000
            if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
        }
        val length = connection.contentLengthLong
        connection.disconnect()
        length.takeIf { it > 0 }
    }.getOrNull()

    /** Re-enqueues the original URL behind a (usually failed) download, then drops the old row. */
    fun retry(context: Context, id: Long): Long? {
        val url = urlPrefs(context).getString(id.toString(), null) ?: return null
        remove(context, id)
        return enqueue(context, url)
    }

    /** Current state of every download this app has started, newest first. */
    fun list(context: Context): List<DownloadInfo> {
        val ids = storedIds(context)
        if (ids.isEmpty()) return emptyList()
        val results = mutableListOf<DownloadInfo>()
        val query = DownloadManager.Query().setFilterById(*ids.toLongArray())
        runCatching {
            downloadManager(context).query(query)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val mediaCol = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                while (cursor.moveToNext()) {
                    results.add(
                        DownloadInfo(
                            id = cursor.getLong(idCol),
                            title = cursor.getString(titleCol) ?: "Download",
                            status = cursor.getInt(statusCol),
                            bytesDownloaded = cursor.getLong(downloadedCol),
                            bytesTotal = cursor.getLong(totalCol),
                            localUri = if (uriCol >= 0) cursor.getString(uriCol) else null,
                            mediaType = if (mediaCol >= 0) cursor.getString(mediaCol) else null,
                        ),
                    )
                }
            }
        }
        // IDs the system no longer knows about (cleared from the downloads app) are pruned
        // so the stored list doesn't grow forever.
        val known = results.map { it.id }.toSet()
        if (known.size != ids.size) storeIds(context, ids.filter { it in known })
        return results.sortedByDescending { it.id }
    }

    /** Cancels a running download or removes a finished one from the list (keeps the file). */
    fun remove(context: Context, id: Long) {
        runCatching { downloadManager(context).remove(id) }
        storeIds(context, storedIds(context).filterNot { it == id })
        urlPrefs(context).edit().remove(id.toString()).apply()
    }

    fun openDownloadedFileUri(context: Context, id: Long): Uri? =
        runCatching { downloadManager(context).getUriForDownloadedFile(id) }.getOrNull()

    private fun storedIds(context: Context): List<Long> =
        prefs(context).getString(KEY_IDS, "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }

    private fun rememberId(context: Context, id: Long) {
        storeIds(context, storedIds(context) + id)
    }

    private fun storeIds(context: Context, ids: List<Long>) {
        prefs(context).edit().putString(KEY_IDS, ids.joinToString(",")).apply()
    }
}
