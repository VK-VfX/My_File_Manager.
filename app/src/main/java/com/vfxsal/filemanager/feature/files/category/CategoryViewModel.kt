package com.vfxsal.filemanager.feature.files.category

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.util.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategoryUiState(
    val category: FileCategory = FileCategory.OTHER,
    val isLoading: Boolean = true,
    val entries: List<FileEntry> = emptyList(),
)

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private var loadedCategory: FileCategory? = null

    fun load(category: FileCategory) {
        if (loadedCategory == category) return
        loadedCategory = category
        fetch(category)
    }

    fun refresh() {
        loadedCategory?.let { fetch(it) }
    }

    private fun fetch(category: FileCategory) {
        viewModelScope.launch {
            _uiState.value = CategoryUiState(category = category, isLoading = true)
            val entries = withContext(Dispatchers.IO) {
                FileOps.filesByCategory(Environment.getExternalStorageDirectory(), category)
                    .sortedByDescending { it.lastModified }
            }
            _uiState.value = CategoryUiState(category = category, isLoading = false, entries = entries)
        }
    }
}
