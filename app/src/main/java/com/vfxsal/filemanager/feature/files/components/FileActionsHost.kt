package com.vfxsal.filemanager.feature.files.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.trash.TrashOps
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.feature.files.util.ZipOps
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the "which file is the details sheet / rename dialog / delete dialog currently
 * about" state so Home, Category and Browser screens can all reuse the same
 * details-sheet-driven quick actions (Open/Share/Rename/Delete) without duplicating it.
 */
@Stable
class FileActionsState {
    var detailsEntry: FileEntry? by mutableStateOf(null)
        private set
    var renameEntry: FileEntry? by mutableStateOf(null)
        private set
    var deleteEntry: FileEntry? by mutableStateOf(null)
        private set

    fun showDetails(entry: FileEntry) {
        detailsEntry = entry
    }

    fun dismissDetails() {
        detailsEntry = null
    }

    fun requestRename(entry: FileEntry) {
        detailsEntry = null
        renameEntry = entry
    }

    fun dismissRename() {
        renameEntry = null
    }

    fun requestDelete(entry: FileEntry) {
        detailsEntry = null
        deleteEntry = entry
    }

    fun dismissDelete() {
        deleteEntry = null
    }
}

@Composable
fun rememberFileActionsState(): FileActionsState = remember { FileActionsState() }

@Composable
fun FileActionsHost(
    state: FileActionsState,
    snackbarHostState: SnackbarHostState,
    onChanged: () -> Unit,
    onEditFile: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    state.detailsEntry?.let { entry ->
        FileDetailsSheet(
            entry = entry,
            onDismiss = { state.dismissDetails() },
            onOpen = {
                state.dismissDetails()
                if (!FileOps.openOrEdit(context, entry, onEditFile)) {
                    scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                }
            },
            onShare = {
                state.dismissDetails()
                if (!FileOps.tryShare(context, listOf(entry.file))) {
                    scope.launch { snackbarHostState.showSnackbar("Unable to share this file") }
                }
            },
            onRename = { state.requestRename(entry) },
            onDelete = { state.requestDelete(entry) },
            onExtract = if (entry.extension.lowercase() == "zip") {
                {
                    state.dismissDetails()
                    scope.launch {
                        val destDir = File(entry.file.parentFile, entry.file.nameWithoutExtension)
                        val success = withContext(Dispatchers.IO) { ZipOps.unzip(entry.file, destDir) }
                        if (success) {
                            onChanged()
                        } else {
                            snackbarHostState.showSnackbar("Could not extract archive")
                        }
                    }
                }
            } else {
                null
            },
        )
    }

    state.renameEntry?.let { entry ->
        TextInputDialog(
            title = "Rename",
            label = "Name",
            initialValue = entry.name,
            confirmLabel = "Rename",
            validator = { input -> validateFileName(input, entry.file.parentFile, entry.name) },
            onDismiss = { state.dismissRename() },
            onConfirm = { newName ->
                state.dismissRename()
                scope.launch {
                    val target = File(entry.file.parentFile, newName)
                    val success = withContext(Dispatchers.IO) { entry.file.renameTo(target) }
                    if (success) {
                        onChanged()
                    } else {
                        snackbarHostState.showSnackbar("Rename failed")
                    }
                }
            },
        )
    }

    state.deleteEntry?.let { entry ->
        DeleteConfirmDialog(
            count = 1,
            onDismiss = { state.dismissDelete() },
            onConfirm = {
                state.dismissDelete()
                scope.launch {
                    withContext(Dispatchers.IO) { TrashOps.moveToTrash(context, entry.file) }
                    onChanged()
                }
            },
        )
    }
}
