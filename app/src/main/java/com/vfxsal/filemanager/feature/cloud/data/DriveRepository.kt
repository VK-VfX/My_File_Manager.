package com.vfxsal.filemanager.feature.cloud.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveApiFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DriveRepository(
    private val context: Context,
    account: GoogleSignInAccount,
) {

    private val drive: Drive = run {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply { selectedAccount = account.account }
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Nimbus Files")
            .build()
    }

    suspend fun listFiles(parentId: String = ROOT_FOLDER_ID): List<DriveFile> = withContext(Dispatchers.IO) {
        val result = drive.files().list()
            .setQ("'$parentId' in parents and trashed = false")
            .setFields("files(id,name,mimeType,size,modifiedTime)")
            .setSpaces("drive")
            .setOrderBy("folder,name")
            .execute()
        result.files.orEmpty().map { it.toDomain() }
    }

    suspend fun createFolder(name: String, parentId: String): DriveFile = withContext(Dispatchers.IO) {
        val metadata = DriveApiFile().apply {
            this.name = name
            this.mimeType = DriveFile.FOLDER_MIME_TYPE
            this.parents = listOf(parentId)
        }
        drive.files().create(metadata)
            .setFields("id,name,mimeType,size,modifiedTime")
            .execute()
            .toDomain()
    }

    suspend fun uploadFile(localUri: Uri, parentId: String): DriveFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(localUri) ?: localUri.lastPathSegment ?: "Upload"
        val mimeType = resolver.getType(localUri) ?: "application/octet-stream"
        val metadata = DriveApiFile().apply {
            this.name = displayName
            this.parents = listOf(parentId)
        }
        val inputStream = resolver.openInputStream(localUri)
            ?: throw IOException("Unable to open $localUri")
        inputStream.use { stream ->
            val mediaContent = InputStreamContent(mimeType, stream)
            drive.files().create(metadata, mediaContent)
                .setFields("id,name,mimeType,size,modifiedTime")
                .execute()
                .toDomain()
        }
    }

    suspend fun downloadFile(fileId: String, destinationFile: File): Unit = withContext(Dispatchers.IO) {
        FileOutputStream(destinationFile).use { output ->
            drive.files().get(fileId).executeMediaAndDownloadTo(output)
        }
    }

    suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        drive.files().delete(fileId).execute()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else {
                null
            }
        }
    }

    private fun DriveApiFile.toDomain(): DriveFile = DriveFile(
        id = id,
        name = name ?: "",
        mimeType = mimeType ?: "",
        sizeBytes = size,
        modifiedTimeMillis = modifiedTime?.value,
    )

    companion object {
        const val ROOT_FOLDER_ID = "root"
    }
}
