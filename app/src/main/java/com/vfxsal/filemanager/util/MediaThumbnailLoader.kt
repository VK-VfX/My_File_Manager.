package com.vfxsal.filemanager.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

/**
 * Shared image+video-frame thumbnail loader for any list that needs to preview a file (not
 * just show a generic category icon) - e.g. Clean's junk/large/duplicate results, so the user
 * can see which photo or video they're about to delete instead of just a filename.
 */
@Composable
fun rememberMediaThumbnailLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}
