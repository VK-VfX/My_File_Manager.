package com.vfxsal.filemanager.feature.browser

import android.app.Application
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HlsDownloadState {
    data object Idle : HlsDownloadState
    data class Downloading(val completed: Int, val total: Int) : HlsDownloadState
    data class Done(val file: File) : HlsDownloadState
    data class Failed(val message: String) : HlsDownloadState
}

/**
 * Drives [HlsDownloader] and holds its progress as UI state. Unlike the rest of the browser's
 * downloads (which go through the system [android.app.DownloadManager] and survive the app being
 * killed), an HLS download is an in-process job the OS knows nothing about - it only survives a
 * configuration change, not a process death. That's an acceptable trade-off here since
 * DownloadManager fundamentally can't do this (it fetches one URL; a stream is a playlist of many
 * that need fetching, decrypting and merging).
 */
class HlsDownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<HlsDownloadState>(HlsDownloadState.Idle)
    val state: StateFlow<HlsDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    fun download(manifestUrl: String, suggestedFileName: String, userAgent: String?) {
        if (job?.isActive == true) return
        _state.value = HlsDownloadState.Downloading(0, 0)
        job = viewModelScope.launch {
            val outputFile = uniqueOutputFile(suggestedFileName)
            HlsDownloader(userAgent)
                .download(manifestUrl, outputFile) { progress ->
                    _state.value = HlsDownloadState.Downloading(progress.completed, progress.total)
                }
                .onSuccess { file ->
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(file.absolutePath), null, null)
                    _state.value = HlsDownloadState.Done(file)
                }
                .onFailure { e ->
                    _state.value = HlsDownloadState.Failed(e.message ?: "Download failed")
                }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = HlsDownloadState.Idle
    }

    /** Clears a terminal (Done/Failed) state once the UI has shown it; leaves an in-flight
     *  download alone. */
    fun dismiss() {
        if (job?.isActive != true) _state.value = HlsDownloadState.Idle
    }

    private fun uniqueOutputFile(baseName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val safeName = baseName.ifBlank { "stream" }
        var candidate = File(downloadsDir, "$safeName.ts")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(downloadsDir, "$safeName ($suffix).ts")
            suffix++
        }
        return candidate
    }

    override fun onCleared() {
        job?.cancel()
    }
}
