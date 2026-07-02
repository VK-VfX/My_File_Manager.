package com.vfxsal.filemanager.feature.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.feature.files.util.FileOps
import java.io.File
import kotlinx.coroutines.Dispatchers
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
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val targetDir = File(targetPath)
                var pasted = 0
                try {
                    snapshot.paths.forEach { sourcePath ->
                        val source = File(sourcePath)
                        if (!source.exists()) return@forEach
                        val targetAbsolute = targetDir.absolutePath
                        val sourceAbsolute = source.absolutePath
                        val isNoOpOrSelfNested = targetAbsolute == sourceAbsolute ||
                            targetAbsolute.startsWith(sourceAbsolute + File.separator)
                        if (isNoOpOrSelfNested) return@forEach
                        when (snapshot.mode) {
                            ClipboardMode.COPY -> FileOps.copyInto(source, targetDir)
                            ClipboardMode.MOVE -> FileOps.moveInto(source, targetDir)
                            null -> Unit
                        }
                        pasted++
                    }
                    PasteResult.Success(pasted)
                } catch (e: Exception) {
                    PasteResult.Failure(e.message ?: "Paste failed")
                }
            }
            if (result is PasteResult.Success) clear()
            onResult(result)
        }
    }
}
