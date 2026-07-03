package com.vfxsal.filemanager.feature.clean.large

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.clean.scan.DashboardScanner
import com.vfxsal.filemanager.feature.clean.scan.LargeFileScanner
import com.vfxsal.filemanager.feature.files.trash.TrashOps
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LargeFilesUiState(
    val isScanning: Boolean = true,
    val isDeleting: Boolean = false,
    val scannedCount: Int = 0,
    val thresholdBytes: Long = DashboardScanner.DEFAULT_LARGE_FILE_THRESHOLD,
    val files: List<FileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val error: String? = null,
) {
    val groupedByCategory: List<Pair<FileCategory, List<FileEntry>>>
        get() = files.groupBy { it.category }
            .toList()
            .sortedByDescending { (_, entries) -> entries.sumOf { it.sizeBytes } }

    val selectedCount: Int get() = selectedPaths.size
    val selectedBytes: Long get() = files.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
}

class LargeFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LargeFilesUiState())
    val uiState: StateFlow<LargeFilesUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    fun setThreshold(bytes: Long) {
        _uiState.update { it.copy(thresholdBytes = bytes) }
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        val threshold = _uiState.value.thresholdBytes
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true, scannedCount = 0, error = null) }
            try {
                val results = LargeFileScanner.scan(threshold) { scanned ->
                    _uiState.update { it.copy(scannedCount = scanned) }
                }
                _uiState.update { it.copy(isScanning = false, files = results, selectedPaths = emptySet()) }
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

    fun toggleCategory(category: FileCategory, select: Boolean) {
        _uiState.update { state ->
            val paths = state.files.filter { it.category == category }.map { it.path }
            val selected = state.selectedPaths.toMutableSet()
            if (select) selected += paths else selected -= paths.toSet()
            state.copy(selectedPaths = selected)
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        val toDelete = state.files.filter { it.path in state.selectedPaths }
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDeleting = true) }
            for (entry in toDelete) {
                try {
                    TrashOps.moveToTrash(getApplication<Application>(), File(entry.path))
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
