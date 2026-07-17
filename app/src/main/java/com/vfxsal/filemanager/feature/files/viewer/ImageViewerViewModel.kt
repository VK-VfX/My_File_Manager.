package com.vfxsal.filemanager.feature.files.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.data.FileIndex
import com.vfxsal.filemanager.feature.files.browse.SortBy
import com.vfxsal.filemanager.feature.files.browse.buildFileEntryComparator
import com.vfxsal.filemanager.feature.files.trash.TrashOps
import com.vfxsal.filemanager.feature.files.util.FileOps
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the swipeable image list comes from: the tapped file's own folder, or every image
 *  in the device-wide Images category (matching what a category screen was showing). */
enum class ImageViewerSource { FOLDER, CATEGORY }

data class ImageViewerUiState(
    val isLoading: Boolean = true,
    val images: List<FileEntry> = emptyList(),
    val initialIndex: Int = 0,
)

class ImageViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    private var loadedForPath: String? = null

    fun load(startPath: String, source: ImageViewerSource, sortBy: SortBy, ascending: Boolean) {
        if (loadedForPath == startPath) return
        loadedForPath = startPath
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val comparator = buildFileEntryComparator(sortBy, ascending)
            val images = withContext(Dispatchers.IO) {
                val target = File(startPath)
                val list = when (source) {
                    ImageViewerSource.CATEGORY ->
                        FileIndex.allFiles().filter { it.category == FileCategory.IMAGES }
                    ImageViewerSource.FOLDER ->
                        FileOps.listChildren(target.parentFile ?: target)
                            .filter { it.category == FileCategory.IMAGES && !it.isDirectory }
                }
                list.sortedWith(comparator)
            }
            val index = images.indexOfFirst { it.path == startPath }.coerceAtLeast(0)
            _uiState.value = ImageViewerUiState(isLoading = false, images = images, initialIndex = index)
        }
    }

    fun deleteAndRemove(entry: FileEntry, permanent: Boolean, onResult: (Boolean) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (permanent) {
                    TrashOps.deletePermanently(context, listOf(entry.file)) == 1
                } else {
                    TrashOps.moveToTrash(context, entry.file)
                }
            }
            if (success) {
                _uiState.update { state -> state.copy(images = state.images.filterNot { it.path == entry.path }) }
            }
            onResult(success)
        }
    }
}
