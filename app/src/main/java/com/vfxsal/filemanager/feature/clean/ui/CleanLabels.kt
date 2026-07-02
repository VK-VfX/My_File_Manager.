package com.vfxsal.filemanager.feature.clean.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.clean.model.JunkCategory

fun FileCategory.label(): String = when (this) {
    FileCategory.FOLDER -> "Folders"
    FileCategory.IMAGES -> "Images"
    FileCategory.VIDEOS -> "Videos"
    FileCategory.AUDIO -> "Audio"
    FileCategory.DOCUMENTS -> "Documents"
    FileCategory.APKS -> "APKs"
    FileCategory.ARCHIVES -> "Archives"
    FileCategory.OTHER -> "Other"
}

fun JunkCategory.label(): String = when (this) {
    JunkCategory.APP_CACHE -> "App cache"
    JunkCategory.JUNK_FILES -> "Junk files"
    JunkCategory.THUMBNAILS -> "Thumbnail caches"
    JunkCategory.EMPTY_FOLDERS -> "Empty folders"
    JunkCategory.APK_INSTALLERS -> "Leftover APK installers"
}

fun JunkCategory.icon(): ImageVector = when (this) {
    JunkCategory.APP_CACHE -> Icons.Filled.Storage
    JunkCategory.JUNK_FILES -> Icons.Filled.InsertDriveFile
    JunkCategory.THUMBNAILS -> Icons.Filled.Image
    JunkCategory.EMPTY_FOLDERS -> Icons.Filled.FolderOff
    JunkCategory.APK_INSTALLERS -> Icons.Filled.Android
}
