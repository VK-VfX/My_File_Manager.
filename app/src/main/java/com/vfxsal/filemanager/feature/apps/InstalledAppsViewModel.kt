package com.vfxsal.filemanager.feature.apps

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
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

data class InstalledApp(
    val packageName: String,
    val label: String,
    val sizeBytes: Long,
    val isSystemApp: Boolean,
    val icon: Bitmap,
)

data class InstalledAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
)

class InstalledAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(InstalledAppsUiState())
    val uiState: StateFlow<InstalledAppsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.packageName != context.packageName }
                    .mapNotNull { appInfo ->
                        runCatching {
                            InstalledApp(
                                packageName = appInfo.packageName,
                                label = pm.getApplicationLabel(appInfo).toString(),
                                sizeBytes = runCatching { File(appInfo.sourceDir).length() }.getOrDefault(0L),
                                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                                icon = pm.getApplicationIcon(appInfo).toBitmap(),
                            )
                        }.getOrNull()
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _uiState.update { it.copy(isLoading = false, apps = apps) }
        }
    }

    fun uninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        context.startActivity(intent)
    }
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
