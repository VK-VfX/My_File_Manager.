package com.vfxsal.filemanager.feature.clean.junk

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.feature.clean.model.JunkCategory
import com.vfxsal.filemanager.feature.clean.model.JunkGroup
import com.vfxsal.filemanager.feature.clean.scan.JunkScanner
import com.vfxsal.filemanager.feature.files.trash.TrashOps
import com.vfxsal.filemanager.util.OperationProgressBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JunkUiState(
    val isScanning: Boolean = true,
    val isDeleting: Boolean = false,
    val scannedCount: Int = 0,
    val groups: List<JunkGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val error: String? = null,
) {
    val selectedCount: Int get() = selectedPaths.size
    val selectedBytes: Long
        get() = groups.flatMap { it.items }.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
}

class JunkFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(JunkUiState())
    val uiState: StateFlow<JunkUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { JunkUiState(isScanning = true) }
            try {
                val groups = JunkScanner.scan(getApplication<Application>()) { scanned ->
                    _uiState.update { it.copy(scannedCount = scanned) }
                }
                val allSelected = groups.flatMap { it.items }.map { it.path }.toSet()
                _uiState.update { it.copy(isScanning = false, groups = groups, selectedPaths = allSelected) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, error = e.message ?: "Scan failed") }
            }
        }
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val selected = state.selectedPaths.toMutableSet()
            if (!selected.remove(path)) selected += path
            state.copy(selectedPaths = selected)
        }
    }

    fun toggleGroup(category: JunkCategory, select: Boolean) {
        _uiState.update { state ->
            val group = state.groups.find { it.category == category } ?: return@update state
            val selected = state.selectedPaths.toMutableSet()
            val groupPaths = group.items.map { it.path }
            if (select) selected += groupPaths else selected -= groupPaths.toSet()
            state.copy(selectedPaths = selected)
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        val itemsToDelete = state.groups.flatMap { it.items }.filter { it.path in state.selectedPaths }
        // Cache contents are regenerated automatically and not worth recovering, so those are
        // deleted outright; everything else goes through trash (in one batched call - trashing
        // items one at a time rewrote the whole trash manifest per item, which made deleting many
        // files at once much slower than it needed to be).
        val (cacheItems, trashItems) = itemsToDelete.partition { it.category == JunkCategory.APP_CACHE }
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDeleting = true) }
            OperationProgressBus.start("Cleaning junk files", itemsToDelete.size)
            try {
                var done = 0
                cacheItems.forEach { item ->
                    try {
                        item.file.listFiles()?.forEach { it.deleteRecursively() }
                    } catch (e: SecurityException) {
                        // Skip files we lost access to mid-scan rather than crashing the whole clean-up.
                    }
                    done++
                    OperationProgressBus.update(done)
                }
                val cacheDone = done
                TrashOps.moveMultipleToTrash(getApplication<Application>(), trashItems.map { it.file }) { movedSoFar, _ ->
                    OperationProgressBus.update(cacheDone + movedSoFar)
                }
            } finally {
                OperationProgressBus.finish()
            }
            _uiState.update { it.copy(isDeleting = false) }
            scan()
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
