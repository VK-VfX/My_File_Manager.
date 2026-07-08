package com.vfxsal.filemanager.feature.wallpaper

import android.app.Application
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.feature.wallpaper.data.WallpaperCatalog
import com.vfxsal.filemanager.feature.wallpaper.data.WallpaperDesign
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val THUMB_WIDTH = 300
private const val THUMB_HEIGHT = 600

// Detailed output resolution: at least full-HD-tall (1440 short side) even on low-res
// panels, and up to 4K (3840 long side) so applied/saved wallpapers stay crisp under the
// launcher's parallax crop. Rendering is all vector math, so higher res just means more
// detail, no assets.
private const val MIN_SHORT_SIDE = 1440
private const val MAX_LONG_SIDE = 3840

enum class WallpaperTarget { HOME, LOCK, BOTH }

sealed interface WallpaperEvent {
    data class Applied(val target: WallpaperTarget) : WallpaperEvent
    data object Saved : WallpaperEvent
    data class Error(val message: String) : WallpaperEvent
}

data class WallpaperThumbnail(
    val design: WallpaperDesign,
    val bitmap: ImageBitmap,
)

data class WallpaperGalleryUiState(
    val thumbnails: List<WallpaperThumbnail> = emptyList(),
    val isLoading: Boolean = true,
)

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WallpaperGalleryUiState())
    val uiState: StateFlow<WallpaperGalleryUiState> = _uiState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<WallpaperEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val thumbnails = withContext(Dispatchers.Default) {
                WallpaperCatalog.designs.map { design ->
                    WallpaperThumbnail(
                        design = design,
                        bitmap = WallpaperRenderer.render(design, THUMB_WIDTH, THUMB_HEIGHT).asImageBitmap(),
                    )
                }
            }
            _uiState.update { it.copy(thumbnails = thumbnails, isLoading = false) }
        }
    }

    fun designFor(id: String): WallpaperDesign? = WallpaperCatalog.designs.firstOrNull { it.id == id }

    /** Target render size: the device's own aspect ratio scaled up so it's crisp and detailed. */
    private fun renderSize(context: Context): Pair<Int, Int> {
        val manager = WallpaperManager.getInstance(context)
        val metrics = context.resources.displayMetrics
        var width = manager.desiredMinimumWidth.takeIf { it > 0 } ?: metrics.widthPixels
        var height = manager.desiredMinimumHeight.takeIf { it > 0 } ?: metrics.heightPixels
        if (width <= 0 || height <= 0) {
            width = 1440
            height = 3120
        }
        val shortSide = minOf(width, height)
        // Scale up so the short side is at least MIN_SHORT_SIDE.
        if (shortSide < MIN_SHORT_SIDE) {
            val factor = MIN_SHORT_SIDE.toFloat() / shortSide
            width = (width * factor).toInt()
            height = (height * factor).toInt()
        }
        // Cap the long side at 4K to bound memory.
        val longSide = maxOf(width, height)
        if (longSide > MAX_LONG_SIDE) {
            val factor = MAX_LONG_SIDE.toFloat() / longSide
            width = (width * factor).toInt()
            height = (height * factor).toInt()
        }
        return width to height
    }

    fun apply(design: WallpaperDesign, target: WallpaperTarget) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isBusy.value = true
            val success = withContext(Dispatchers.Default) {
                runCatching {
                    val manager = WallpaperManager.getInstance(context)
                    val (width, height) = renderSize(context)
                    val bitmap = WallpaperRenderer.render(design, width, height)
                    val flags = when (target) {
                        WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                        WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        manager.setBitmap(bitmap, null, true, flags)
                    } else {
                        manager.setBitmap(bitmap)
                    }
                    bitmap.recycle()
                }.isSuccess
            }
            _isBusy.value = false
            _events.emit(if (success) WallpaperEvent.Applied(target) else WallpaperEvent.Error("Could not set wallpaper"))
        }
    }

    fun save(design: WallpaperDesign) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _isBusy.value = true
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val (width, height) = renderSize(context)
                    val bitmap = WallpaperRenderer.render(design, width, height)
                    saveBitmapToGallery(context, bitmap, design.name)
                    bitmap.recycle()
                }.isSuccess
            }
            _isBusy.value = false
            _events.emit(if (success) WallpaperEvent.Saved else WallpaperEvent.Error("Could not save wallpaper"))
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String) {
        val fileName = "${name.replace(" ", "_")}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WhatFiles Wallpapers")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create MediaStore entry")
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                ?: throw IOException("Could not open output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WhatFiles Wallpapers")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        }
    }
}
