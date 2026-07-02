package com.vfxsal.filemanager.feature.clean.duplicates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.feature.clean.model.DuplicateGroup
import com.vfxsal.filemanager.feature.clean.model.DuplicateScanPhase
import com.vfxsal.filemanager.feature.clean.scan.DuplicateScanner
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuplicateUiState(
    val isScanning: Boolean = true,
    val isDeleting: Boolean = false,
    val phase: DuplicateScanPhase = DuplicateScanPhase.SIZE_BUCKETING,
    val progressCount: Int = 0,
    val groups: List<DuplicateGroup> = emptyList(),
    /** hash -> path of the copy to keep. */
    val keepers: Map<String, String> = emptyMap(),
    val selectedPaths: Set<String> = emptySet(),
    val error: String? = null,
) {
    val selectedCount: Int get() = selectedPaths.size
    val selectedBytes: Long
        get() = groups.flatMap { it.files }.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
}

class DuplicateFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DuplicateUiState())
    val uiState: StateFlow<DuplicateUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { DuplicateUiState(isScanning = true) }
            try {
                val groups = DuplicateScanner.scan { phase, count ->
                    _uiState.update { it.copy(phase = phase, progressCount = count) }
                }
                val keepers = groups.associate { it.hash to it.files.first().path }
                val selected = groups.flatMap { it.files.drop(1) }.map { it.path }.toSet()
                _uiState.update {
                    it.copy(isScanning = false, groups = groups, keepers = keepers, selectedPaths = selected)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, error = e.message ?: "Scan failed") }
            }
        }
    }

    fun setKeeper(group: DuplicateGroup, keepPath: String) {
        _uiState.update { state ->
            val selected = state.selectedPaths.toMutableSet()
            group.files.forEach { entry ->
                if (entry.path == keepPath) selected.remove(entry.path) else selected.add(entry.path)
            }
            state.copy(keepers = state.keepers + (group.hash to keepPath), selectedPaths = selected)
        }
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val isKeeper = state.keepers.values.contains(path)
            if (isKeeper) return@update state
            val selected = state.selectedPaths.toMutableSet()
            if (!selected.remove(path)) selected += path
            state.copy(selectedPaths = selected)
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        val toDelete = state.groups.flatMap { it.files }.filter { it.path in state.selectedPaths }
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDeleting = true) }
            for (entry in toDelete) {
                try {
                    File(entry.path).delete()
                } catch (e: SecurityException) {
                    // Skip files we lost access to mid-scan rather than crashing the whole clean-up.
                }
            }
            _uiState.update { it.copy(isDeleting = false) }
            scan()
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
