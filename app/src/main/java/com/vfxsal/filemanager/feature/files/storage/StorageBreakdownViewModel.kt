package com.vfxsal.filemanager.feature.files.storage

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.home.CategorySummary
import com.vfxsal.filemanager.util.StorageStats
import com.vfxsal.filemanager.util.StorageStatsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageBreakdownUiState(
    val isLoading: Boolean = true,
    val stats: StorageStats? = null,
    val summaries: List<CategorySummary> = emptyList(),
)

class StorageBreakdownViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StorageBreakdownUiState())
    val uiState: StateFlow<StorageBreakdownUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (stats, summaries) = withContext(Dispatchers.IO) {
                val root = Environment.getExternalStorageDirectory()
                val allFiles = mutableListOf<FileEntry>()
                root.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        runCatching { FileEntry.from(file) }.getOrNull()?.let { allFiles.add(it) }
                    }
                }
                val summaries = FileCategory.entries
                    .filter { it != FileCategory.FOLDER }
                    .map { category ->
                        val matches = allFiles.filter { it.category == category }
                        CategorySummary(category, matches.size, matches.sumOf { it.sizeBytes })
                    }
                    .filter { it.count > 0 }
                    .sortedByDescending { it.totalBytes }
                StorageStatsUtils.primaryStorageStats() to summaries
            }
            _uiState.update { it.copy(isLoading = false, stats = stats, summaries = summaries) }
        }
    }
}
