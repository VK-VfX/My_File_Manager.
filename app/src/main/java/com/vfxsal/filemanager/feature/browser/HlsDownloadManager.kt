package com.vfxsal.filemanager.feature.browser

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HlsJobStatus {
    data class Downloading(val completed: Int, val total: Int) : HlsJobStatus
    data class Done(val file: File) : HlsJobStatus
    data class Failed(val message: String) : HlsJobStatus
}

data class HlsJob(val id: String, val title: String, val status: HlsJobStatus)

/**
 * Tracks in-flight HLS downloads at the process level instead of scoping them to a single
 * screen's ViewModel - a screen-scoped job used to get silently cancelled the moment the user
 * closed the browser (its NavBackStackEntry, and the ViewModelStore tied to it, gets cleared),
 * which looked to users like downloads randomly failing. A plain singleton with its own
 * [CoroutineScope] survives navigation and backgrounding; it's still lost on process death, same
 * as any other in-process job (unlike the system DownloadManager used for direct file downloads,
 * which the OS tracks independently of this app's process).
 */
object HlsDownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runningJobs = ConcurrentHashMap<String, Job>()

    private val _jobs = MutableStateFlow<List<HlsJob>>(emptyList())
    val jobs: StateFlow<List<HlsJob>> = _jobs.asStateFlow()

    fun enqueue(context: Context, manifestUrl: String, suggestedTitle: String, userAgent: String?): String {
        val id = UUID.randomUUID().toString()
        val title = suggestedTitle.ifBlank { "stream" }
        _jobs.update { it + HlsJob(id, title, HlsJobStatus.Downloading(0, 0)) }

        val appContext = context.applicationContext
        val job = scope.launch {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            HlsDownloader(userAgent)
                .download(manifestUrl, downloadsDir, title) { progress ->
                    setStatus(id, HlsJobStatus.Downloading(progress.completed, progress.total))
                }
                .onSuccess { file ->
                    MediaScannerConnection.scanFile(appContext, arrayOf(file.absolutePath), null, null)
                    setStatus(id, HlsJobStatus.Done(file))
                }
                .onFailure { e ->
                    setStatus(id, HlsJobStatus.Failed(e.message ?: "Download failed"))
                }
            runningJobs.remove(id)
        }
        runningJobs[id] = job
        return id
    }

    fun cancel(id: String) {
        runningJobs.remove(id)?.cancel()
        dismiss(id)
    }

    /** Drops a finished (or cancelled) job from the list; leaves an in-flight download alone. */
    fun dismiss(id: String) {
        _jobs.update { list -> list.filterNot { it.id == id } }
    }

    private fun setStatus(id: String, status: HlsJobStatus) {
        _jobs.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }
}
