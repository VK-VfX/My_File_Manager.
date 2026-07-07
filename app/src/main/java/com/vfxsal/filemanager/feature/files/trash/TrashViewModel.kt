package com.vfxsal.filemanager.feature.files.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.util.OperationProgressBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrashUiState(
    val isLoading: Boolean = true,
    val entries: List<TrashOps.TrashEntry> = emptyList(),
)

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            val entries = withContext(Dispatchers.IO) {
                TrashOps.purgeExpired(context)
                TrashOps.listEntries(context).sortedByDescending { it.trashedAtMillis }
            }
            _uiState.update { it.copy(isLoading = false, entries = entries) }
        }
    }

    fun restore(entry: TrashOps.TrashEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { TrashOps.restore(getApplication<Application>(), entry) }
            if (success) load()
            onResult(success)
        }
    }

    fun deleteForever(entry: TrashOps.TrashEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { TrashOps.deleteForever(getApplication<Application>(), entry) }
            if (success) load()
            onResult(success)
        }
    }

    fun emptyTrash(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val count = withContext(Dispatchers.IO) {
                val entries = TrashOps.listEntries(context)
                OperationProgressBus.start("Emptying recycle bin", entries.size)
                try {
                    var done = 0
                    entries.count { entry ->
                        val deleted = TrashOps.deleteForever(context, entry)
                        done++
                        OperationProgressBus.update(done)
                        deleted
                    }
                } finally {
                    OperationProgressBus.finish()
                }
            }
            load()
            onResult(count)
        }
    }
}
