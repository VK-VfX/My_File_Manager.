package com.vfxsal.filemanager.feature.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.feature.video.data.VideoFolder
import com.vfxsal.filemanager.feature.video.data.VideoItem
import com.vfxsal.filemanager.feature.video.data.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoGalleryUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val allVideos: List<VideoItem> = emptyList(),
    val folders: List<VideoFolder> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
)

/**
 * Scoped to the "video" nav graph's root back stack entry (see VideoNavGraph.kt) so that the
 * gallery, per-folder grid, and player screen all share one instance. The player needs a whole
 * folder's video list for prev/next navigation, which can't travel through nav arguments (those
 * are string-only), so it's stashed here in [folderQueues] keyed by folder name instead.
 */
class VideoGalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val _uiState = MutableStateFlow(VideoGalleryUiState())
    val uiState: StateFlow<VideoGalleryUiState> = _uiState.asStateFlow()

    private val folderQueues = mutableMapOf<String, List<VideoItem>>()

    fun loadVideos(force: Boolean = false) {
        if (_uiState.value.hasLoaded && !force) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val videos = repository.queryVideos().sortedByDescending { it.dateModifiedSeconds }
            val folders = videos
                .groupBy { it.bucketName }
                .map { (name, items) -> VideoFolder(name, items) }
                .sortedBy { it.name.lowercase() }
            _uiState.update {
                it.copy(isLoading = false, hasLoaded = true, allVideos = videos, folders = folders)
            }
        }
    }

    fun setQueue(key: String, videos: List<VideoItem>) {
        folderQueues[key] = videos
    }

    fun queueFor(key: String): List<VideoItem> = folderQueues[key].orEmpty()

    fun enterSelectionMode(id: Long) {
        _uiState.update { it.copy(selectionMode = true, selectedIds = setOf(id)) }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val newSelection = if (state.selectedIds.contains(id)) state.selectedIds - id else state.selectedIds + id
            state.copy(selectedIds = newSelection, selectionMode = newSelection.isNotEmpty())
        }
    }

    fun selectAll(candidates: List<VideoItem>) {
        _uiState.update { it.copy(selectedIds = candidates.map { video -> video.id }.toSet(), selectionMode = true) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
    }

    fun deleteSelected(candidates: List<VideoItem>, onResult: (Int) -> Unit) {
        val selected = _uiState.value.selectedIds
        val targets = candidates.filter { it.id in selected }
        viewModelScope.launch {
            val deletedIds = withContext(Dispatchers.IO) {
                targets.filter { repository.deleteVideo(it) }.map { it.id }.toSet()
            }
            if (deletedIds.isNotEmpty()) {
                val updatedVideos = _uiState.value.allVideos.filterNot { it.id in deletedIds }
                val updatedFolders = updatedVideos
                    .groupBy { it.bucketName }
                    .map { (name, items) -> VideoFolder(name, items) }
                    .sortedBy { it.name.lowercase() }
                _uiState.update {
                    it.copy(
                        allVideos = updatedVideos,
                        folders = updatedFolders,
                        selectedIds = emptySet(),
                        selectionMode = false,
                    )
                }
                folderQueues.replaceAll { _, queue -> queue.filterNot { item -> item.id in deletedIds } }
            } else {
                clearSelection()
            }
            onResult(deletedIds.size)
        }
    }

    companion object {
        const val ALL_VIDEOS_QUEUE_KEY = "__all_videos__"
    }
}
