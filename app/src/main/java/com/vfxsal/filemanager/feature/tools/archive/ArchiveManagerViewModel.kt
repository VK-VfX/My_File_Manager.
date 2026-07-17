package com.vfxsal.filemanager.feature.tools.archive

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.util.ArchiveOps
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.util.OperationProgressBus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ArchiveUiState(
    val path: String,
    val entries: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val dir: File get() = File(path)
}

/** A focused single-folder browser whose only job is to compress selections and extract archives. */
class ArchiveManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val root: String = Environment.getExternalStorageDirectory().absolutePath

    private val _uiState = MutableStateFlow(ArchiveUiState(path = root))
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    init {
        load(root)
    }

    fun open(path: String) = load(path)

    fun goUp() {
        val parent = _uiState.value.dir.parentFile ?: return
        load(parent.absolutePath)
    }

    fun canGoUp(): Boolean = _uiState.value.dir.parentFile != null

    private fun load(path: String) {
        _uiState.update { it.copy(path = path, isLoading = true, selected = emptySet()) }
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                FileOps.listChildren(File(path)).sortedWith(
                    compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() },
                )
            }
            _uiState.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    fun refresh() = load(_uiState.value.path)

    fun toggleSelect(path: String) {
        _uiState.update { state ->
            val next = if (path in state.selected) state.selected - path else state.selected + path
            state.copy(selected = next)
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selected = emptySet()) }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun selectedFiles(): List<File> {
        val state = _uiState.value
        return state.entries.filter { it.path in state.selected }.map { it.file }
    }

    val singleExtractable: File?
        get() {
            val sel = _uiState.value.selected
            if (sel.size != 1) return null
            val file = File(sel.first())
            return if (ArchiveOps.isExtractable(file)) file else null
        }

    fun compress(format: ArchiveOps.Format) {
        val sources = selectedFiles()
        if (sources.isEmpty()) return
        val dir = _uiState.value.dir
        val baseName = if (sources.size == 1) sources.first().nameWithoutExtension else dir.name.ifBlank { "archive" }
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                OperationProgressBus.start("Compressing to ${format.label}", 0)
                try {
                    ArchiveOps.compress(sources, dir, baseName, format)
                } finally {
                    OperationProgressBus.finish()
                }
            }
            clearSelection()
            refresh()
            _uiState.update {
                it.copy(busy = false, message = if (result != null) "Created ${result.name}" else "Compression failed")
            }
        }
    }

    fun extract(archive: File) {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                OperationProgressBus.start("Extracting ${archive.name}", 0)
                try {
                    ArchiveOps.extract(archive)
                } finally {
                    OperationProgressBus.finish()
                }
            }
            clearSelection()
            refresh()
            _uiState.update {
                it.copy(busy = false, message = if (result != null) "Extracted to ${result.name}" else "Extraction failed")
            }
        }
    }
}
