package com.vfxsal.filemanager.feature.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    /** Queries the device MediaStore for every audio track flagged as music. */
    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val albumArtUri = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleCol) ?: UNKNOWN_TITLE,
                    artist = cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: UNKNOWN_ARTIST,
                    album = cursor.getString(albumCol) ?: UNKNOWN_ALBUM,
                    albumId = albumId,
                    durationMs = cursor.getLong(durationCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    contentUri = contentUri,
                    albumArtUri = albumArtUri,
                )
            }
        }
        tracks
    }

    fun groupByAlbum(tracks: List<Track>): List<AlbumSummary> {
        return tracks
            .groupBy { it.albumId }
            .map { (albumId, albumTracks) ->
                val first = albumTracks.first()
                AlbumSummary(
                    albumId = albumId,
                    title = first.album,
                    artist = first.artist,
                    albumArtUri = first.albumArtUri,
                    tracks = albumTracks,
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    fun groupByArtist(tracks: List<Track>): List<ArtistSummary> {
        return tracks
            .groupBy { it.artist }
            .map { (artist, artistTracks) ->
                ArtistSummary(
                    name = artist,
                    albumCount = artistTracks.map { it.albumId }.distinct().size,
                    tracks = artistTracks,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    companion object {
        private val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
        private const val UNKNOWN_TITLE = "Unknown title"
        private const val UNKNOWN_ARTIST = "Unknown artist"
        private const val UNKNOWN_ALBUM = "Unknown album"
    }
}
