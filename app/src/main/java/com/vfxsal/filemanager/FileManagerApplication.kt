package com.vfxsal.filemanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.vfxsal.filemanager.data.FileIndex

class FileManagerApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        createPlaybackNotificationChannel()
        // Warm the storage index in the background so the first screen reads a ready snapshot
        // instead of blocking on a cold walk.
        FileIndex.prime()
    }

    /**
     * One process-wide ImageLoader (memory + disk cache) rather than every thumbnail row
     * building its own - sharing it is what makes `context.imageLoader` (used throughout the
     * app for file/media thumbnails) actually cache across scrolls instead of re-decoding.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()

    private fun createPlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "music_playback"
    }
}
