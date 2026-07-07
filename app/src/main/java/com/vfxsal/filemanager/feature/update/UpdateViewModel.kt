package com.vfxsal.filemanager.feature.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateUiState(
    val isChecking: Boolean = false,
    val checkedOnce: Boolean = false,
    val available: ReleaseInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val error: String? = null,
    val needsInstallPermission: Boolean = false,
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val currentVersionName: String
        get() {
            val context = getApplication<Application>()
            return runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        }

    /**
     * [silent] suppresses the "up to date"/"check failed" states so a background check on
     * app launch doesn't flash unwanted UI - only a genuine update result updates state.
     * Silent checks are also throttled to once per day; the Files home runs one on every
     * visit, and hammering the GitHub API for that is pointless. Manual checks always run.
     */
    fun checkForUpdate(silent: Boolean = false) {
        val context = getApplication<Application>()
        if (silent) {
            val lastCheck = updatePrefs(context).getLong(KEY_LAST_SILENT_CHECK, 0L)
            if (System.currentTimeMillis() - lastCheck < TimeUnit.DAYS.toMillis(1)) return
            updatePrefs(context).edit().putLong(KEY_LAST_SILENT_CHECK, System.currentTimeMillis()).apply()
        }
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isChecking = true, error = null) }
            val release = withContext(Dispatchers.IO) { UpdateChecker.fetchLatestRelease() }
            val version = currentVersionName
            _uiState.update { state ->
                when {
                    release != null && UpdateChecker.isNewer(release.versionName, version) ->
                        state.copy(isChecking = false, checkedOnce = true, available = release, error = null)
                    silent -> state.copy(isChecking = false)
                    release == null -> state.copy(isChecking = false, checkedOnce = true, error = "Could not check for updates")
                    else -> state.copy(isChecking = false, checkedOnce = true, available = null)
                }
            }
        }
    }

    fun downloadAndInstall() {
        val release = _uiState.value.available ?: return
        val context = getApplication<Application>()
        if (!UpdateOps.canInstallPackages(context)) {
            _uiState.update { it.copy(needsInstallPermission = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0, error = null) }
            val apkFile = withContext(Dispatchers.IO) {
                UpdateOps.downloadApk(context, release.downloadUrl) { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            }
            _uiState.update { it.copy(isDownloading = false) }
            if (apkFile != null) {
                UpdateOps.installApk(context, apkFile)
            } else {
                _uiState.update { it.copy(error = "Download failed") }
            }
        }
    }

    fun openInstallPermissionSettings() {
        _uiState.update { it.copy(needsInstallPermission = false) }
        UpdateOps.requestInstallPermission(getApplication())
    }

    fun dismissInstallPermissionPrompt() {
        _uiState.update { it.copy(needsInstallPermission = false) }
    }

    fun dismiss() {
        _uiState.update { it.copy(available = null, error = null) }
    }

    private fun updatePrefs(context: Context) =
        context.getSharedPreferences(UPDATE_PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val UPDATE_PREFS_NAME = "update_checks"
        const val KEY_LAST_SILENT_CHECK = "last_silent_check"
    }
}
