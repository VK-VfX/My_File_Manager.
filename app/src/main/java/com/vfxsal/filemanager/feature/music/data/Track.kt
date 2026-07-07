package com.vfxsal.filemanager.feature.music.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val contentUri: Uri,
    val albumArtUri: Uri,
)

data class AlbumSummary(
    val albumId: Long,
    val title: String,
    val artist: String,
    val albumArtUri: Uri,
    val tracks: List<Track>,
)

data class ArtistSummary(
    val name: String,
    val albumCount: Int,
    val tracks: List<Track>,
)

/** Builds a Media3 item carrying enough metadata for the system notification and lock screen. */
fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(albumArtUri)
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(metadata)
        .build()
}
