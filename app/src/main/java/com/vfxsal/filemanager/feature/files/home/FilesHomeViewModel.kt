package com.vfxsal.filemanager.feature.files.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.data.FileIndex
import com.vfxsal.filemanager.util.FormatUtils
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

data class HomeSuggestion(
    val message: String,
    val category: FileCategory,
)

data class FilesHomeUiState(
    val isLoading: Boolean = true,
    val storageStats: StorageStats? = null,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val recentFiles: List<FileEntry> = emptyList(),
    val downloadsCount: Int = 0,
    val downloadsBytes: Long = 0,
    val suggestions: List<HomeSuggestion> = emptyList(),
)

private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
private const val SCREENSHOT_BUILDUP_THRESHOLD = 3
private const val ACTIVE_CATEGORY_THRESHOLD = 3

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

    private suspend fun computeState(): FilesHomeUiState {
        val root = Environment.getExternalStorageDirectory()
        val allFiles = FileIndex.allFiles()
        val summaries = FileCategory.entries
            .filter { it != FileCategory.FOLDER && it != FileCategory.OTHER }
            .map { category ->
                val matches = allFiles.filter { it.category == category }
                CategorySummary(category, matches.size, matches.sumOf { it.sizeBytes })
            }
        val recent = allFiles.sortedByDescending { it.lastModified }.take(20)

        val downloadsPrefix = File(root, "Download").absolutePath + File.separator
        val downloadFiles = allFiles.filter { it.path.startsWith(downloadsPrefix) }

        return FilesHomeUiState(
            isLoading = false,
            storageStats = StorageStatsUtils.primaryStorageStats(),
            categorySummaries = summaries,
            recentFiles = recent,
            downloadsCount = downloadFiles.size,
            downloadsBytes = downloadFiles.sumOf { it.sizeBytes },
            suggestions = buildSuggestions(allFiles),
        )
    }

    /** Surfaces quick-action nudges from data already computed for the home screen, e.g.
     *  a buildup of screenshots or a category that's seen unusually heavy activity lately. */
    private fun buildSuggestions(allFiles: List<FileEntry>): List<HomeSuggestion> {
        val weekAgo = System.currentTimeMillis() - WEEK_MILLIS
        val recentFiles = allFiles.filter { it.lastModified >= weekAgo }
        val suggestions = mutableListOf<HomeSuggestion>()

        val screenshotCount = recentFiles.count {
            it.category == FileCategory.IMAGES && it.name.contains("screenshot", ignoreCase = true)
        }
        if (screenshotCount >= SCREENSHOT_BUILDUP_THRESHOLD) {
            suggestions += HomeSuggestion(
                message = "$screenshotCount screenshots added this week - clean them up?",
                category = FileCategory.IMAGES,
            )
        }

        val busiest = recentFiles
            .filter { it.category != FileCategory.OTHER && it.category != FileCategory.FOLDER }
            .groupBy { it.category }
            .maxByOrNull { (_, files) -> files.size }
        if (busiest != null && busiest.value.size >= ACTIVE_CATEGORY_THRESHOLD) {
            val (category, files) = busiest
            suggestions += HomeSuggestion(
                message = "${files.size} ${categoryLabel(category).lowercase()} added this week (${FormatUtils.formatFileSize(files.sumOf { it.sizeBytes })})",
                category = category,
            )
        }

        return suggestions.take(2)
    }
}
