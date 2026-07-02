package com.vfxsal.filemanager.feature.music.ui

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Returns a callback that requests POST_NOTIFICATIONS (API 33+ only) the first time it's
 * invoked without a grant. Playback itself never depends on this permission - the service plays
 * audio either way - it only controls whether the system notification is visible, so callers
 * fire this alongside starting playback rather than gating playback behind it.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberNotificationPermissionRequester(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return {}
    }
    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    return {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }
}
