package com.vfxsal.filemanager.feature.cloud.data

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedTimeMillis: Long?,
) {
    val isFolder: Boolean get() = mimeType == FOLDER_MIME_TYPE

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}
