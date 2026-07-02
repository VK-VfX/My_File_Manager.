package com.vfxsal.filemanager.feature.cloud

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PickedMediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val selected: Boolean = true,
)

data class PhotosImportUiState(
    val pickedItems: List<PickedMediaItem> = emptyList(),
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val importedCount: Int? = null,
    val error: String? = null,
)

class PhotosImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhotosImportUiState())
    val uiState: StateFlow<PhotosImportUiState> = _uiState.asStateFlow()

    fun onItemsPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        val items = uris.map { uri ->
            val type = resolver.getType(uri).orEmpty()
            PickedMediaItem(uri = uri, isVideo = type.startsWith("video/"))
        }
        _uiState.update { it.copy(pickedItems = items, importedCount = null, error = null) }
    }

    fun toggleSelection(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                pickedItems = state.pickedItems.map { item ->
                    if (item.uri == uri) item.copy(selected = !item.selected) else item
                },
            )
        }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importedCount = null) }
    }

    fun importSelected() {
        val selected = _uiState.value.pickedItems.filter { it.selected }
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(isImporting = true, importProgress = 0, importTotal = selected.size, error = null)
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            var copied = 0
            for (item in selected) {
                val ok = withContext(Dispatchers.IO) { copyToMediaStore(context, item) }
                if (ok) copied++
                _uiState.update { it.copy(importProgress = it.importProgress + 1) }
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importedCount = copied,
                    pickedItems = emptyList(),
                    importProgress = 0,
                    importTotal = 0,
                )
            }
        }
    }

    private fun copyToMediaStore(context: Context, item: PickedMediaItem): Boolean {
        return try {
            val resolver = context.contentResolver
            val displayName = queryDisplayName(context, item.uri)
                ?: "Nimbus_${System.currentTimeMillis()}${if (item.isVideo) ".mp4" else ".jpg"}"
            val mimeType = resolver.getType(item.uri) ?: if (item.isVideo) "video/*" else "image/*"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(
                        if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                    )
                    if (!dir.exists()) dir.mkdirs()
                    put(MediaStore.MediaColumns.DATA, File(dir, displayName).absolutePath)
                }
            }

            val collection = if (item.isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val newUri = resolver.insert(collection, values) ?: return false

            val copiedOk = resolver.openOutputStream(newUri)?.use { output ->
                resolver.openInputStream(item.uri)?.use { input ->
                    input.copyTo(output)
                    true
                } ?: false
            } ?: false

            if (!copiedOk) {
                resolver.delete(newUri, null, null)
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pendingValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(newUri, pendingValues, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else {
                null
            }
        }
    }
}
