package com.vfxsal.filemanager.feature.files.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.data.FileIndex
import com.vfxsal.filemanager.feature.files.tags.FileTagsStore
import com.vfxsal.filemanager.feature.files.trash.TrashOps
import com.vfxsal.filemanager.feature.files.util.BatchRenameOps
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.feature.files.util.RenamePattern
import com.vfxsal.filemanager.feature.files.util.ZipOps
import com.vfxsal.filemanager.feature.files.vault.VaultOps
import com.vfxsal.filemanager.util.OperationProgressBus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortBy { NAME, SIZE, DATE, TYPE }

data class DirectoryBrowserUiState(
    val path: String = "",
    val isLoading: Boolean = true,
    val entries: List<FileEntry> = emptyList(),
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchRecursive: Boolean = false,
    val isSearchingRecursive: Boolean = false,
)

class DirectoryBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DirectoryBrowserUiState())
    val uiState: StateFlow<DirectoryBrowserUiState> = _uiState.asStateFlow()

    private var rawEntries: List<FileEntry> = emptyList()
    private var recursiveEntries: List<FileEntry>? = null
    private var recursiveJob: Job? = null
    private var loadedPath: String? = null

    fun load(path: String) {
        if (loadedPath == path) return
        loadedPath = path
        _uiState.value = DirectoryBrowserUiState(path = path, isLoading = true)
        fetchChildren()
    }

    fun refresh() = fetchChildren()

    private fun fetchChildren() {
        val path = _uiState.value.path
        recursiveEntries = null
        recursiveJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val loaded = withContext(Dispatchers.IO) { FileOps.listChildren(File(path)) }
            rawEntries = loaded
            _uiState.update { it.copy(isLoading = false) }
            recompute()
        }
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.update {
            if (it.sortBy == sortBy) it.copy(ascending = !it.ascending) else it.copy(sortBy = sortBy, ascending = true)
        }
        recompute()
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

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                searchActive = active,
                searchQuery = if (!active) "" else it.searchQuery,
                selectionMode = false,
                selectedPaths = emptySet(),
            )
        }
        recompute()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recompute()
    }

    fun setSearchRecursive(recursive: Boolean) {
        _uiState.update { it.copy(searchRecursive = recursive) }
        if (recursive && recursiveEntries == null) {
            loadRecursiveEntries()
        } else {
            recompute()
        }
    }

    private fun loadRecursiveEntries() {
        recursiveJob?.cancel()
        val path = _uiState.value.path
        recursiveJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingRecursive = true) }
            val entries = withContext(Dispatchers.IO) { FileOps.scanRecursive(File(path)) }
            recursiveEntries = entries
            _uiState.update { it.copy(isSearchingRecursive = false) }
            recompute()
        }
    }

    private fun recompute() {
        val state = _uiState.value
        val comparator = buildComparator(state.sortBy, state.ascending)
        val display = if (state.searchActive && state.searchQuery.isNotBlank()) {
            val source = if (state.searchRecursive) recursiveEntries.orEmpty() else rawEntries
            source.filter { it.name.contains(state.searchQuery, ignoreCase = true) }.sortedWith(comparator)
        } else {
            rawEntries.sortedWith(comparator)
        }
        _uiState.update { it.copy(entries = display) }
    }

    private fun buildComparator(sortBy: SortBy, ascending: Boolean): Comparator<FileEntry> =
        buildFileEntryComparator(sortBy, ascending)

    fun createFolder(name: String, onResult: (Boolean) -> Unit) {
        val parent = File(_uiState.value.path)
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { File(parent, name).mkdir() }
            if (success) {
                FileIndex.invalidate()
                fetchChildren()
            }
            onResult(success)
        }
    }

    fun renameEntry(entry: FileEntry, newName: String, onResult: (Boolean) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val dest = File(entry.file.parentFile, newName)
                val renamed = entry.file.renameTo(dest)
                if (renamed) {
                    FileTagsStore.onPathMoved(context, entry.path, dest.absolutePath)
                    FileIndex.invalidate()
                }
                renamed
            }
            clearSelection()
            if (success) fetchChildren()
            onResult(success)
        }
    }

    fun deleteSelected(permanent: Boolean, onResult: (Int) -> Unit) {
        val targets = _uiState.value.selectedPaths.map { File(it) }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                OperationProgressBus.start("Deleting ${targets.size} items", targets.size)
                try {
                    if (permanent) {
                        TrashOps.deletePermanently(context, targets) { done, _ -> OperationProgressBus.update(done) }
                    } else {
                        TrashOps.moveMultipleToTrash(context, targets) { done, _ -> OperationProgressBus.update(done) }
                    }
                } finally {
                    OperationProgressBus.finish()
                }
            }
            clearSelection()
            fetchChildren()
            onResult(deleted)
        }
    }

    fun compressSelected(onResult: (Boolean) -> Unit) {
        val entries = selectedEntries()
        if (entries.isEmpty()) {
            onResult(false)
            return
        }
        val targetDir = File(_uiState.value.path)
        val destName = if (entries.size == 1) "${entries.first().name}.zip" else "Archive.zip"
        viewModelScope.launch {
            val dest = FileOps.uniqueDestination(targetDir, destName)
            val success = withContext(Dispatchers.IO) { ZipOps.zip(entries.map { it.file }, dest) }
            if (success) {
                FileIndex.invalidate()
                clearSelection()
                fetchChildren()
            }
            onResult(success)
        }
    }

    fun moveSelectedToVault(onResult: (Int) -> Unit) {
        val targets = _uiState.value.selectedPaths.map { File(it) }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val moved = withContext(Dispatchers.IO) { targets.count { VaultOps.moveIn(context, it) } }
            clearSelection()
            fetchChildren()
            onResult(moved)
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
                fetchChildren()
            }
            onResult(renamed)
        }
    }

    fun selectedEntries(): List<FileEntry> {
        val selected = _uiState.value.selectedPaths
        return rawEntries.filter { selected.contains(it.path) }
    }

    fun siblingNames(): Set<String> = rawEntries.map { it.name }.toSet()
}

/** Shared by [DirectoryBrowserViewModel] and [com.vfxsal.filemanager.feature.files.category.CategoryViewModel]
 *  so both the directory browser and category screens sort files the same way. */
fun buildFileEntryComparator(sortBy: SortBy, ascending: Boolean): Comparator<FileEntry> {
    val base: Comparator<FileEntry> = when (sortBy) {
        SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortBy.SIZE -> compareBy { it.sizeBytes }
        SortBy.DATE -> compareBy { it.lastModified }
        SortBy.TYPE -> compareBy<FileEntry> { it.category.ordinal }
            .thenComparing(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val directional = if (ascending) base else base.reversed()
    return compareByDescending<FileEntry> { it.isDirectory }.then(directional)
}
