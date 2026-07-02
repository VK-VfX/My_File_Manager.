package com.vfxsal.filemanager.feature.files.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.util.StorageStats
import com.vfxsal.filemanager.util.StorageStatsUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategorySummary(
    val category: FileCategory,
    val count: Int,
    val totalBytes: Long,
)

data class FilesHomeUiState(
    val isLoading: Boolean = true,
    val storageStats: StorageStats? = null,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val recentFiles: List<FileEntry> = emptyList(),
    val downloadsCount: Int = 0,
    val downloadsBytes: Long = 0,
)

class FilesHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FilesHomeUiState())
    val uiState: StateFlow<FilesHomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val newState = withContext(Dispatchers.IO) { computeState() }
            _uiState.value = newState
        }
    }

    private fun computeState(): FilesHomeUiState {
        val root = Environment.getExternalStorageDirectory()
        val allFiles = mutableListOf<FileEntry>()
        root.walkTopDown().forEach { file ->
            if (file.isFile) {
                runCatching { FileEntry.from(file) }.getOrNull()?.let { allFiles.add(it) }
            }
        }
        val summaries = FileCategory.entries
            .filter { it != FileCategory.FOLDER && it != FileCategory.OTHER }
            .map { category ->
                val matches = allFiles.filter { it.category == category }
                CategorySummary(category, matches.size, matches.sumOf { it.sizeBytes })
            }
        val recent = allFiles.sortedByDescending { it.lastModified }.take(20)

        val downloadsDir = File(root, "Download")
        val downloadFiles = if (downloadsDir.isDirectory) {
            FileOps.scanRecursive(downloadsDir).filterNot { it.isDirectory }
        } else {
            emptyList()
        }

        return FilesHomeUiState(
            isLoading = false,
            storageStats = StorageStatsUtils.primaryStorageStats(),
            categorySummaries = summaries,
            recentFiles = recent,
            downloadsCount = downloadFiles.size,
            downloadsBytes = downloadFiles.sumOf { it.sizeBytes },
        )
    }
}
