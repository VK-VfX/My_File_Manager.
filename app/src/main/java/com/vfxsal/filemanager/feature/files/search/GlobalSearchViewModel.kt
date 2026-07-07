package com.vfxsal.filemanager.feature.files.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.data.FileIndex
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
            _uiState.update { it.copy(isSearching = true) }
            // Filtering the shared FileIndex snapshot instead of re-walking the whole tree
            // per keystroke: the first search after a cold start pays one scan, every
            // refinement after that is an in-memory filter.
            val matches = withContext(Dispatchers.IO) {
                FileIndex.allEntries().filter { it.name.contains(query, ignoreCase = true) }
            }
            _uiState.update { it.copy(isSearching = false, results = matches) }
        }
    }
}
