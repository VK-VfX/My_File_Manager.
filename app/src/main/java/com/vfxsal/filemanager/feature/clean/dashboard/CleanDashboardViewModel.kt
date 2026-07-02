package com.vfxsal.filemanager.feature.clean.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.clean.scan.DashboardScanner
import com.vfxsal.filemanager.util.StorageStats
import com.vfxsal.filemanager.util.StorageStatsUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CleanTeaser(val itemCount: Int, val totalBytes: Long)

data class CleanDashboardUiState(
    val isScanning: Boolean = true,
    val storageStats: StorageStats? = null,
    val categoryTotals: Map<FileCategory, Long> = emptyMap(),
    val junkTeaser: CleanTeaser = CleanTeaser(0, 0),
    val largeTeaser: CleanTeaser = CleanTeaser(0, 0),
    val duplicateTeaser: CleanTeaser = CleanTeaser(0, 0),
    val error: String? = null,
)

class CleanDashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CleanDashboardUiState())
    val uiState: StateFlow<CleanDashboardUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val stats = StorageStatsUtils.primaryStorageStats()
                _uiState.update { it.copy(storageStats = stats) }

                val result = DashboardScanner.scan()
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        categoryTotals = result.categoryTotals,
                        junkTeaser = CleanTeaser(result.junkTeaserCount, result.junkTeaserBytes),
                        largeTeaser = CleanTeaser(result.largeTeaserCount, result.largeTeaserBytes),
                        duplicateTeaser = CleanTeaser(result.duplicateTeaserCount, result.duplicateTeaserBytes),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, error = e.message ?: "Scan failed") }
            }
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
