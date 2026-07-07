package com.vfxsal.filemanager.feature.files.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zips a set of files/folders straight into a user-picked SAF tree (e.g. an SD card or a
 * cloud-synced folder) as a single timestamped archive. Uses `DocumentsContract` directly
 * rather than the `androidx.documentfile` artifact, since `ActivityResultContracts.OpenDocumentTree`
 * already gives everything needed without a new dependency.
 */
object BackupOps {

    fun backupToTree(context: Context, treeUri: Uri, sources: List<File>, archiveBaseName: String): Boolean {
        if (sources.isEmpty()) return false
        return try {
            val dirUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileUri = DocumentsContract.createDocument(
                context.contentResolver,
                dirUri,
                "application/zip",
                "$archiveBaseName-$stamp.zip",
            ) ?: return false
            context.contentResolver.openOutputStream(fileUri)?.use { output ->
                ZipOps.zip(sources, output)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
