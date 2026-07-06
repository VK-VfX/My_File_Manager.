package com.vfxsal.filemanager.feature.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileIndex
import com.vfxsal.filemanager.feature.files.tags.FileTagsStore
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.util.OperationProgressBus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClipboardMode { COPY, MOVE }

data class ClipboardState(
    val paths: List<String> = emptyList(),
    val mode: ClipboardMode? = null,
) {
    val isEmpty: Boolean get() = paths.isEmpty() || mode == null
}

sealed interface PasteResult {
    data class Success(val count: Int) : PasteResult
    data class Failure(val message: String) : PasteResult
}

/**
 * Scoped to the Files nav graph's own back-stack entry (see FilesNavGraph.kt) so the
 * "Paste (N)" affordance survives navigation between directory browser screens until
 * the user pastes or cancels it.
 */
class ClipboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ClipboardState())
    val state: StateFlow<ClipboardState> = _state.asStateFlow()

    fun copy(paths: List<String>) {
        if (paths.isEmpty()) return
        _state.value = ClipboardState(paths, ClipboardMode.COPY)
    }

    fun cut(paths: List<String>) {
        if (paths.isEmpty()) return
        _state.value = ClipboardState(paths, ClipboardMode.MOVE)
    }

    fun clear() {
        _state.value = ClipboardState()
    }

    fun pasteInto(targetPath: String, onResult: (PasteResult) -> Unit) {
        val snapshot = _state.value
        if (snapshot.isEmpty) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            // NonCancellable so navigating between folders (which can recreate this VM's
            // scope) doesn't cancel a transfer mid-file and leave a half-copied mess.
            val result = withContext(Dispatchers.IO + NonCancellable) {
                val targetDir = File(targetPath)
                var pasted = 0
                val verb = if (snapshot.mode == ClipboardMode.MOVE) "Moving" else "Copying"
                OperationProgressBus.start("$verb ${snapshot.paths.size} items", snapshot.paths.size)
                try {
                    snapshot.paths.forEachIndexed { index, sourcePath ->
                        val source = File(sourcePath)
                        val targetAbsolute = targetDir.absolutePath
                        val sourceAbsolute = source.absolutePath
                        val isNoOpOrSelfNested = targetAbsolute == sourceAbsolute ||
                            targetAbsolute.startsWith(sourceAbsolute + File.separator)
                        if (source.exists() && !isNoOpOrSelfNested) {
                            when (snapshot.mode) {
                                ClipboardMode.COPY -> FileOps.copyInto(source, targetDir)
                                ClipboardMode.MOVE -> {
                                    val dest = FileOps.moveInto(source, targetDir)
                                    FileTagsStore.onPathMoved(context, sourcePath, dest.absolutePath)
                                }
                                null -> Unit
                            }
                            pasted++
                        }
                        OperationProgressBus.update(index + 1)
                    }
                    PasteResult.Success(pasted)
                } catch (e: Exception) {
                    PasteResult.Failure(e.message ?: "Paste failed")
                } finally {
                    OperationProgressBus.finish()
                    FileIndex.invalidate()
                }
            }
            if (result is PasteResult.Success) clear()
            onResult(result)
        }
    }
}
