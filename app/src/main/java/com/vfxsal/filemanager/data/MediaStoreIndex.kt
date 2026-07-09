package com.vfxsal.filemanager.data

import android.content.Context
import android.provider.MediaStore

/**
 * Fast category listing via MediaStore. Walking the whole external-storage tree to find every
 * image/video/audio file is slow on large libraries; the media scanner already maintains an
 * index of exactly those, so for the Images/Videos/Audio categories we query it directly and
 * skip the walk. Documents/APKs/archives aren't fully covered by MediaStore, so those still
 * fall back to [FileIndex].
 */
object MediaStoreIndex {

    fun query(context: Context, category: FileCategory): List<FileEntry> = when (category) {
        FileCategory.IMAGES -> collect(
            context,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        FileCategory.VIDEOS -> collect(
            context,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
        FileCategory.AUDIO -> collect(
            context,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        else -> emptyList()
    }

    fun isSupported(category: FileCategory): Boolean =
        category == FileCategory.IMAGES || category == FileCategory.VIDEOS || category == FileCategory.AUDIO

    private fun collect(
        context: Context,
        uri: android.net.Uri,
        dataColumn: String,
        sizeColumn: String,
        dateColumn: String,
    ): List<FileEntry> {
        val projection = arrayOf(dataColumn, sizeColumn, dateColumn)
        val results = mutableListOf<FileEntry>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataIdx = cursor.getColumnIndexOrThrow(dataColumn)
                val sizeIdx = cursor.getColumnIndexOrThrow(sizeColumn)
                val dateIdx = cursor.getColumnIndexOrThrow(dateColumn)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIdx) ?: continue
                    val file = java.io.File(path)
                    if (!file.exists()) continue
                    results.add(
                        FileEntry(
                            path = path,
                            name = file.name,
                            isDirectory = false,
                            sizeBytes = cursor.getLong(sizeIdx),
                            // MediaStore stores DATE_MODIFIED in seconds; FileEntry uses millis.
                            lastModified = cursor.getLong(dateIdx) * 1000L,
                            category = FileCategory.fromExtension(file.extension),
                        ),
                    )
                }
            }
        }
        return results
    }
}
