package com.vfxsal.filemanager.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vfxsal.filemanager.ui.theme.CategoryApks
import com.vfxsal.filemanager.ui.theme.CategoryArchives
import com.vfxsal.filemanager.ui.theme.CategoryAudio
import com.vfxsal.filemanager.ui.theme.CategoryDocuments
import com.vfxsal.filemanager.ui.theme.CategoryImages
import com.vfxsal.filemanager.ui.theme.CategoryOther
import com.vfxsal.filemanager.ui.theme.CategoryVideos

enum class FileCategory {
    FOLDER, IMAGES, VIDEOS, AUDIO, DOCUMENTS, APKS, ARCHIVES, OTHER;

    val icon: ImageVector
        get() = when (this) {
            FOLDER -> Icons.Filled.Folder
            IMAGES -> Icons.Filled.Image
            VIDEOS -> Icons.Filled.Movie
            AUDIO -> Icons.Filled.AudioFile
            DOCUMENTS -> Icons.Filled.Description
            APKS -> Icons.Filled.Android
            ARCHIVES -> Icons.Filled.FolderZip
            OTHER -> Icons.Filled.InsertDriveFile
        }

    @Composable
    fun color(): Color = when (this) {
        FOLDER -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        IMAGES -> CategoryImages
        VIDEOS -> CategoryVideos
        AUDIO -> CategoryAudio
        DOCUMENTS -> CategoryDocuments
        APKS -> CategoryApks
        ARCHIVES -> CategoryArchives
        OTHER -> CategoryOther
    }

    companion object {
        private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
        private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv")
        private val audioExt = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")
        private val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt", "rtf")
        private val apkExt = setOf("apk", "apks", "xapk")
        private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

        fun fromExtension(extension: String): FileCategory {
            val ext = extension.lowercase()
            return when {
                ext in imageExt -> IMAGES
                ext in videoExt -> VIDEOS
                ext in audioExt -> AUDIO
                ext in docExt -> DOCUMENTS
                ext in apkExt -> APKS
                ext in archiveExt -> ARCHIVES
                else -> OTHER
            }
        }

        fun fromMimeType(mimeType: String?): FileCategory {
            if (mimeType == null) return OTHER
            return when {
                mimeType.startsWith("image/") -> IMAGES
                mimeType.startsWith("video/") -> VIDEOS
                mimeType.startsWith("audio/") -> AUDIO
                mimeType == "application/pdf" ||
                    mimeType.contains("document") ||
                    mimeType.contains("msword") ||
                    mimeType.contains("spreadsheet") ||
                    mimeType.contains("presentation") ||
                    mimeType == "text/plain" -> DOCUMENTS
                mimeType == "application/vnd.android.package-archive" -> APKS
                mimeType.contains("zip") || mimeType.contains("compressed") -> ARCHIVES
                else -> OTHER
            }
        }
    }
}
