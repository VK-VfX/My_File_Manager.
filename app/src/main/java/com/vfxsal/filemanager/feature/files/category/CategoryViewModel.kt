package com.vfxsal.filemanager.feature.files.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.data.FileIndex
import com.vfxsal.filemanager.feature.files.browse.SortBy
import com.vfxsal.filemanager.feature.files.browse.buildFileEntryComparator
import com.vfxsal.filemanager.feature.files.tags.FileTagsStore
import com.vfxsal.filemanager.feature.files.trash.TrashOps
import com.vfxsal.filemanager.feature.files.util.BatchRenameOps
import com.vfxsal.filemanager.feature.files.util.RenamePattern
import com.vfxsal.filemanager.feature.files.vault.VaultOps
import com.vfxsal.filemanager.feature.settings.CategoryViewMode
import com.vfxsal.filemanager.feature.settings.SettingsStore
import com.vfxsal.filemanager.util.OperationProgressBus
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
    val sortBy: SortBy = SortBy.DATE,
    val ascending: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val viewMode: CategoryViewMode = CategoryViewMode.GRID,
)

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        CategoryUiState(viewMode = SettingsStore.getCategoryViewMode(application)),
    )
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    fun setViewMode(mode: CategoryViewMode) {
        SettingsStore.setCategoryViewMode(getApplication(), mode)
        _uiState.update { it.copy(viewMode = mode) }
    }

    private var loadedCategory: FileCategory? = null
    private var rawEntries: List<FileEntry> = emptyList()

    fun load(category: FileCategory) {
        if (loadedCategory == category) return
        loadedCategory = category
        fetch(category)
    }

    fun refresh() {
        loadedCategory?.let { fetch(it) }
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.update {
            if (it.sortBy == sortBy) it.copy(ascending = !it.ascending) else it.copy(sortBy = sortBy, ascending = true)
        }
        recompute()
    }

    private fun fetch(category: FileCategory) {
        val sortBy = _uiState.value.sortBy
        val ascending = _uiState.value.ascending
        val viewMode = _uiState.value.viewMode
        viewModelScope.launch {
            _uiState.value = CategoryUiState(
                category = category,
                isLoading = true,
                sortBy = sortBy,
                ascending = ascending,
                viewMode = viewMode,
            )
            rawEntries = withContext(Dispatchers.IO) {
                FileIndex.allFiles().filter { it.category == category }
            }
            _uiState.update { it.copy(isLoading = false) }
            recompute()
        }
    }

    private fun recompute() {
        val state = _uiState.value
        val sorted = rawEntries.sortedWith(buildFileEntryComparator(state.sortBy, state.ascending))
        _uiState.update { it.copy(entries = sorted) }
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
            val deleted = withContext(Dispatchers.IO) {
                OperationProgressBus.start("Deleting ${targets.size} items", targets.size)
                try {
                    var done = 0
                    targets.count { target ->
                        val moved = TrashOps.moveToTrash(context, target)
                        done++
                        OperationProgressBus.update(done)
                        moved
                    }
                } finally {
                    OperationProgressBus.finish()
                }
            }
            clearSelection()
            loadedCategory?.let { fetch(it) }
            onResult(deleted)
        }
    }

    fun renameSelected(baseName: String, pattern: RenamePattern, onResult: (Int) -> Unit) {
        val entries = _uiState.value.entries.filter { it.path in _uiState.value.selectedPaths }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) {
                BatchRenameOps.rename(entries.map { it.file }, baseName, pattern) { oldPath, newPath ->
                    FileTagsStore.onPathMoved(context, oldPath, newPath)
                }
            }
            clearSelection()
            if (renamed > 0) {
                FileIndex.invalidate()
                loadedCategory?.let { fetch(it) }
            }
            onResult(renamed)
        }
    }

    fun moveSelectedToVault(onResult: (Int) -> Unit) {
        val targets = _uiState.value.selectedPaths.map { File(it) }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val moved = withContext(Dispatchers.IO) { targets.count { VaultOps.moveIn(context, it) } }
            clearSelection()
            loadedCategory?.let { fetch(it) }
            onResult(moved)
        }
    }
}
