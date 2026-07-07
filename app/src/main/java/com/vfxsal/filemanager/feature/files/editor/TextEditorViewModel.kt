package com.vfxsal.filemanager.feature.files.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A single BasicTextField holding megabytes of text becomes unusably slow, so files past this size open read-only-elsewhere instead. */
private const val MAX_EDITABLE_BYTES = 2L * 1024 * 1024

enum class LoadStatus { LOADING, LOADED, TOO_LARGE, ERROR }

data class TextEditorUiState(
    val path: String = "",
    val fileName: String = "",
    val status: LoadStatus = LoadStatus.LOADING,
    val text: String = "",
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
)

class TextEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TextEditorUiState())
    val uiState: StateFlow<TextEditorUiState> = _uiState.asStateFlow()

    private var originalText: String = ""
    private var loadedPath: String? = null

    fun load(path: String) {
        if (loadedPath == path) return
        loadedPath = path
        val file = File(path)
        _uiState.value = TextEditorUiState(path = path, fileName = file.name, status = LoadStatus.LOADING)
        viewModelScope.launch {
            if (file.length() > MAX_EDITABLE_BYTES) {
                _uiState.update { it.copy(status = LoadStatus.TOO_LARGE) }
                return@launch
            }
            val content = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }
            if (content == null) {
                _uiState.update { it.copy(status = LoadStatus.ERROR) }
            } else {
                originalText = content
                _uiState.update { it.copy(status = LoadStatus.LOADED, text = content, isDirty = false) }
            }
        }
    }

    fun updateText(text: String) {
        _uiState.update { it.copy(text = text, isDirty = text != originalText) }
    }

    fun save(onResult: (Boolean) -> Unit) {
        val state = _uiState.value
        if (state.status != LoadStatus.LOADED) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val success = withContext(Dispatchers.IO) {
                runCatching { File(state.path).writeText(state.text) }.isSuccess
            }
            if (success) originalText = state.text
            _uiState.update { it.copy(isSaving = false, isDirty = if (success) false else it.isDirty) }
            onResult(success)
        }
    }
}
