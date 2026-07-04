package com.vfxsal.filemanager.feature.files.category

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
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

data class CategoryUiState(
    val category: FileCategory = FileCategory.OTHER,
    val isLoading: Boolean = true,
    val entries: List<FileEntry> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
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

    fun enterSelectionMode(path: String) {
        _uiState.update { it.copy(selectionMode = true, selectedPaths = setOf(path)) }
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedPaths.contains(path)) {
                state.selectedPaths - path
            } else {
                state.selectedPaths + path
            }
            state.copy(selectedPaths = newSelection, selectionMode = newSelection.isNotEmpty())
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedPaths = it.entries.map { e -> e.path }.toSet(), selectionMode = true) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet(), selectionMode = false) }
    }

    fun selectedEntries(): List<FileEntry> {
        val selected = _uiState.value.selectedPaths
        return _uiState.value.entries.filter { selected.contains(it.path) }
    }

    fun deleteSelected(onResult: (Int) -> Unit) {
        val targets = _uiState.value.selectedPaths.map { File(it) }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { targets.count { TrashOps.moveToTrash(context, it) } }
            clearSelection()
            loadedCategory?.let { fetch(it) }
            onResult(deleted)
        }
    }
}
