package com.vfxsal.filemanager.feature.video.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val bucketName: String,
    val width: Int,
    val height: Int,
    val relativePath: String?,
)
