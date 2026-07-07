package com.vfxsal.filemanager.feature.video.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

/**
 * One decoder-equipped [ImageLoader] shared by every thumbnail on a screen, rather than one per
 * grid item: building an ImageLoader spins up its own memory/disk caches and dispatcher, so
 * reusing a single instance is both cheaper and lets thumbnails share the cache.
 */
@Composable
fun rememberVideoImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
}
