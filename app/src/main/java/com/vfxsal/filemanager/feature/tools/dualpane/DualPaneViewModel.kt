package com.vfxsal.filemanager.feature.tools.dualpane

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

enum class PaneSide { LEFT, RIGHT }

data class PaneUiState(
    val path: String,
    val entries: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
) {
    val dir: File get() = File(path)
}

data class DualPaneUiState(
    val left: PaneUiState,
    val right: PaneUiState,
    val busy: Boolean = false,
) {
    fun pane(side: PaneSide) = if (side == PaneSide.LEFT) left else right
}

/**
 * Two independent directory browsers. Each pane tracks its own path, listing and selection;
 * transfers copy or move between them. All filesystem work is off the main thread and batch
 * operations surface through the app-wide [OperationProgressBus] overlay.
 */
class DualPaneViewModel(application: Application) : AndroidViewModel(application) {

    private val root: String = Environment.getExternalStorageDirectory().absolutePath

    private val _uiState = MutableStateFlow(
        DualPaneUiState(
            left = PaneUiState(path = root),
            right = PaneUiState(path = root),
        ),
    )
    val uiState: StateFlow<DualPaneUiState> = _uiState.asStateFlow()

    init {
        load(PaneSide.LEFT, root)
        load(PaneSide.RIGHT, root)
    }

    private fun other(side: PaneSide) = if (side == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT

    private fun updatePane(side: PaneSide, transform: (PaneUiState) -> PaneUiState) {
        _uiState.update { state ->
            if (side == PaneSide.LEFT) state.copy(left = transform(state.left))
            else state.copy(right = transform(state.right))
        }
    }

    fun open(side: PaneSide, path: String) = load(side, path)

    fun goUp(side: PaneSide) {
        val parent = _uiState.value.pane(side).dir.parentFile ?: return
        load(side, parent.absolutePath)
    }

    fun canGoUp(side: PaneSide): Boolean = _uiState.value.pane(side).dir.parentFile != null

    private fun load(side: PaneSide, path: String) {
        updatePane(side) { it.copy(path = path, isLoading = true, selected = emptySet()) }
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                FileOps.listChildren(File(path)).sortedWith(
                    compareByDescending<FileEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() },
                )
            }
            updatePane(side) { it.copy(entries = entries, isLoading = false) }
        }
    }

    fun refresh(side: PaneSide) = load(side, _uiState.value.pane(side).path)

    fun toggleSelect(side: PaneSide, path: String) {
        updatePane(side) { pane ->
            val next = if (path in pane.selected) pane.selected - path else pane.selected + path
            pane.copy(selected = next)
        }
    }

    fun clearSelection(side: PaneSide) = updatePane(side) { it.copy(selected = emptySet()) }

    fun selectedFiles(side: PaneSide): List<File> {
        val pane = _uiState.value.pane(side)
        return pane.entries.filter { it.path in pane.selected }.map { it.file }
    }

    /** Copies or moves the current selection in [from] into the opposite pane's directory. */
    fun transferSelection(from: PaneSide, move: Boolean) {
        val sources = selectedFiles(from)
        if (sources.isEmpty()) return
        val targetDir = _uiState.value.pane(other(from)).dir
        runBatch(if (move) "Moving ${sources.size} items" else "Copying ${sources.size} items", sources.size) { onEach ->
            sources.forEach { src ->
                runCatching { if (move) FileOps.moveInto(src, targetDir) else FileOps.copyInto(src, targetDir) }
                onEach()
            }
        }.invokeOnCompletion {
            clearSelection(from)
            refresh(from)
            refresh(other(from))
        }
    }

    /** Single-file drag-and-drop drop handler: copies [source] from [from] to the opposite pane. */
    fun dropCopy(from: PaneSide, source: File) {
        val targetDir = _uiState.value.pane(other(from)).dir
        if (source.parentFile?.absolutePath == targetDir.absolutePath) return
        runBatch("Copying ${source.name}", 1) { onEach ->
            runCatching { FileOps.copyInto(source, targetDir) }
            onEach()
        }.invokeOnCompletion { refresh(other(from)) }
    }

    fun deleteSelection(side: PaneSide) {
        val targets = selectedFiles(side)
        if (targets.isEmpty()) return
        runBatch("Deleting ${targets.size} items", targets.size) { onEach ->
            targets.forEach { runCatching { FileOps.delete(it) }; onEach() }
        }.invokeOnCompletion {
            clearSelection(side)
            refresh(side)
        }
    }

    fun compressSelection(side: PaneSide, format: ArchiveOps.Format) {
        val sources = selectedFiles(side)
        if (sources.isEmpty()) return
        val dir = _uiState.value.pane(side).dir
        val baseName = if (sources.size == 1) sources.first().nameWithoutExtension else dir.name.ifBlank { "archive" }
        setBusy(true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OperationProgressBus.start("Compressing to ${format.label}", 0)
                try {
                    ArchiveOps.compress(sources, dir, baseName, format)
                } finally {
                    OperationProgressBus.finish()
                }
            }
            clearSelection(side)
            refresh(side)
            setBusy(false)
        }
    }

    fun extract(side: PaneSide, archive: File) {
        setBusy(true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                OperationProgressBus.start("Extracting ${archive.name}", 0)
                try {
                    ArchiveOps.extract(archive)
                } finally {
                    OperationProgressBus.finish()
                }
            }
            refresh(side)
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) = _uiState.update { it.copy(busy = busy) }

    private fun runBatch(label: String, total: Int, block: suspend (onEach: () -> Unit) -> Unit) =
        viewModelScope.launch {
            setBusy(true)
            withContext(Dispatchers.IO) {
                OperationProgressBus.start(label, total)
                var done = 0
                try {
                    block { done++; OperationProgressBus.update(done) }
                } finally {
                    OperationProgressBus.finish()
                }
            }
            setBusy(false)
        }
}
