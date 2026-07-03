package com.vfxsal.filemanager.feature.files.search

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.clean.scan.FileTreeWalker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GlobalSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<FileEntry> = emptyList(),
)

private const val DEBOUNCE_MILLIS = 300L
private const val RESULT_BATCH_SIZE = 15

class GlobalSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            _uiState.update { it.copy(isSearching = true, results = emptyList()) }
            val root = Environment.getExternalStorageDirectory()
            val matches = mutableListOf<FileEntry>()
            withContext(Dispatchers.IO) {
                FileTreeWalker.walk(
                    root = root,
                    onFile = { file ->
                        if (file.name.contains(query, ignoreCase = true)) {
                            runCatching { FileEntry.from(file) }.getOrNull()?.let { entry ->
                                matches.add(entry)
                                if (matches.size % RESULT_BATCH_SIZE == 0) {
                                    _uiState.update { it.copy(results = matches.toList()) }
                                }
                            }
                        }
                    },
                    onDirectory = { dir, _ ->
                        if (dir != root && dir.name.contains(query, ignoreCase = true)) {
                            runCatching { FileEntry.from(dir) }.getOrNull()?.let { matches.add(it) }
                        }
                    },
                )
            }
            _uiState.update { it.copy(isSearching = false, results = matches.toList()) }
        }
    }
}
