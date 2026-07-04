package com.vfxsal.filemanager.feature.files.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultUiState(
    val hasPin: Boolean = false,
    val isUnlocked: Boolean = false,
    val isLoading: Boolean = true,
    val entries: List<VaultOps.VaultEntry> = emptyList(),
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    fun checkPinStatus() {
        _uiState.update { it.copy(hasPin = VaultOps.hasPin(getApplication<Application>())) }
    }

    fun createPin(pin: String) {
        VaultOps.setPin(getApplication<Application>(), pin)
        _uiState.update { it.copy(hasPin = true, isUnlocked = true) }
        load()
    }

    fun unlock(pin: String, onResult: (Boolean) -> Unit) {
        val success = VaultOps.verifyPin(getApplication<Application>(), pin)
        if (success) {
            _uiState.update { it.copy(isUnlocked = true) }
            load()
        }
        onResult(success)
    }

    fun lock() {
        _uiState.update { it.copy(isUnlocked = false, entries = emptyList()) }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            val entries = withContext(Dispatchers.IO) {
                VaultOps.listEntries(context).sortedByDescending { it.addedAtMillis }
            }
            _uiState.update { it.copy(isLoading = false, entries = entries) }
        }
    }

    fun restore(entry: VaultOps.VaultEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { VaultOps.restore(getApplication<Application>(), entry) }
            if (success) load()
            onResult(success)
        }
    }

    fun deleteForever(entry: VaultOps.VaultEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { VaultOps.deleteForever(getApplication<Application>(), entry) }
            if (success) load()
            onResult(success)
        }
    }
}
