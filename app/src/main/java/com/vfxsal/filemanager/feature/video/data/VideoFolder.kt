package com.vfxsal.filemanager.feature.video.data

import android.net.Uri

data class VideoFolder(
    val name: String,
    val videos: List<VideoItem>,
) {
    val count: Int get() = videos.size
    val coverUri: Uri? get() = videos.firstOrNull()?.uri
}
