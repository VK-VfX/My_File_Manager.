package com.vfxsal.filemanager.feature.video.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * Queries MediaStore directly (no Room cache): the video library changes whenever the user
 * adds/removes files outside the app, so a fresh query per screen-load is simpler and always
 * correct rather than maintaining a stale local copy.
 */
class VideoRepository(private val context: Context) {

    fun queryVideos(): List<VideoItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            if (hasRelativePath) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val videos = mutableListOf<VideoItem>()

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val relativePathCol = if (hasRelativePath) {
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                videos += VideoItem(
                    id = id,
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: uri.lastPathSegment.orEmpty(),
                    durationMs = cursor.getLong(durationCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    dateModifiedSeconds = cursor.getLong(dateCol),
                    bucketName = cursor.getString(bucketCol) ?: UNKNOWN_BUCKET,
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) else null,
                )
            }
        }

        return videos
    }

    /**
     * Deletes both the underlying file and its MediaStore index entry in one call. Relies on
     * the app's existing MANAGE_EXTERNAL_STORAGE grant (already required for the Files/Clean
     * features) to avoid the per-item RecoverableSecurityException consent dialog that a
     * scoped-storage app without broad access would otherwise hit here.
     */
    fun deleteVideo(video: VideoItem): Boolean {
        return runCatching { context.contentResolver.delete(video.uri, null, null) > 0 }.getOrDefault(false)
    }

    companion object {
        const val UNKNOWN_BUCKET = "Other"
    }
}
