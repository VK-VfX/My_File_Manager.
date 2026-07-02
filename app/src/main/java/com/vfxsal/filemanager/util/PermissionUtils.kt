package com.vfxsal.filemanager.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Centralizes the permission checks the app needs across API levels:
 * - API 30+ (R): full storage access requires the special MANAGE_EXTERNAL_STORAGE grant,
 *   given via a Settings screen rather than the normal runtime prompt.
 * - API 33+ (Tiramisu): granular per-media-type read permissions replace
 *   READ_EXTERNAL_STORAGE for gallery/library-style access.
 * - API < 33: a single READ_EXTERNAL_STORAGE runtime permission covers all media.
 */
object PermissionUtils {

    /** Whether the app can browse the full shared file system (Files/Clean features). */
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Launches the system settings screen to grant MANAGE_EXTERNAL_STORAGE. */
    fun allFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
    }

    /** Legacy runtime permission needed on API < 30 before MANAGE_EXTERNAL_STORAGE existed. */
    fun legacyStoragePermission(): String = Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasMediaPermission(context: Context, category: MediaPermissionType): Boolean {
        val permission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> category.granularPermission
            else -> Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    enum class MediaPermissionType(val granularPermission: String) {
        IMAGES(Manifest.permission.READ_MEDIA_IMAGES),
        VIDEO(Manifest.permission.READ_MEDIA_VIDEO),
        AUDIO(Manifest.permission.READ_MEDIA_AUDIO),
    }
}
