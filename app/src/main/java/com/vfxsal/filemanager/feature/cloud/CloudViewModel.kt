package com.vfxsal.filemanager.feature.cloud

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.vfxsal.filemanager.feature.cloud.data.DriveFile
import com.vfxsal.filemanager.feature.cloud.data.DriveRepository
import com.vfxsal.filemanager.feature.cloud.data.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DriveFolderRef(val id: String, val name: String)

data class CloudAccount(
    val displayName: String,
    val email: String,
    val photoUrl: Uri?,
)

sealed interface CloudAuthState {
    data object Loading : CloudAuthState
    data object SignedOut : CloudAuthState
    data class SignedIn(val account: CloudAccount) : CloudAuthState
}

data class DriveBrowserUiState(
    val breadcrumbs: List<DriveFolderRef> = listOf(DriveFolderRef(DriveRepository.ROOT_FOLDER_ID, "My Drive")),
    val items: List<DriveFile> = emptyList(),
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
)

class CloudViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = GoogleAuthManager(application)

    private var driveRepository: DriveRepository? = null

    private val _authState = MutableStateFlow<CloudAuthState>(CloudAuthState.Loading)
    val authState: StateFlow<CloudAuthState> = _authState.asStateFlow()

    private val _browserState = MutableStateFlow(DriveBrowserUiState())
    val browserState: StateFlow<DriveBrowserUiState> = _browserState.asStateFlow()

    private val openFileChannel = Channel<Intent>(Channel.BUFFERED)
    val openFileEvents = openFileChannel.receiveAsFlow()

    val signInIntent: Intent get() = authManager.signInIntent

    init {
        restoreSignIn()
    }

    private fun restoreSignIn() {
        applyAccount(authManager.lastSignedInAccount(getApplication()))
    }

    fun onSignInResult(data: Intent?) {
        val account = authManager.handleSignInResult(data)
        if (account != null) {
            applyAccount(account)
        } else {
            _authState.value = CloudAuthState.SignedOut
        }
    }

    private fun applyAccount(account: GoogleSignInAccount?) {
        if (account == null) {
            driveRepository = null
            _authState.value = CloudAuthState.SignedOut
            return
        }
        driveRepository = DriveRepository(getApplication(), account)
        _authState.value = CloudAuthState.SignedIn(
            CloudAccount(
                displayName = account.displayName ?: account.email.orEmpty(),
                email = account.email.orEmpty(),
                photoUrl = account.photoUrl,
            ),
        )
        _browserState.value = DriveBrowserUiState()
        loadCurrentFolder()
    }

    fun signOut() {
        authManager.signOut {
            driveRepository = null
            _authState.value = CloudAuthState.SignedOut
            _browserState.value = DriveBrowserUiState()
        }
    }

    fun refresh() = loadCurrentFolder()

    private fun loadCurrentFolder() {
        val repository = driveRepository ?: return
        val folderId = _browserState.value.breadcrumbs.last().id
        _browserState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.listFiles(folderId) }
                .onSuccess { files ->
                    _browserState.update { it.copy(items = files, isLoading = false) }
                }
                .onFailure { e ->
                    _browserState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to load Drive files")
                    }
                }
        }
    }

    fun openFolder(folder: DriveFile) {
        if (!folder.isFolder) return
        _browserState.update { it.copy(breadcrumbs = it.breadcrumbs + DriveFolderRef(folder.id, folder.name)) }
        loadCurrentFolder()
    }

    fun navigateToBreadcrumb(index: Int) {
        _browserState.update { it.copy(breadcrumbs = it.breadcrumbs.take(index + 1)) }
        loadCurrentFolder()
    }

    /** Pops one level of the internal folder back-stack. Returns false once at the root. */
    fun navigateUp(): Boolean {
        val crumbs = _browserState.value.breadcrumbs
        if (crumbs.size <= 1) return false
        _browserState.update { it.copy(breadcrumbs = crumbs.dropLast(1)) }
        loadCurrentFolder()
        return true
    }

    fun createFolder(name: String) {
        val repository = driveRepository ?: return
        if (name.isBlank()) return
        val parentId = _browserState.value.breadcrumbs.last().id
        _browserState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            runCatching { repository.createFolder(name.trim(), parentId) }
                .onSuccess { loadCurrentFolder() }
                .onFailure { e ->
                    _browserState.update {
                        it.copy(isBusy = false, error = e.message ?: "Failed to create folder")
                    }
                }
        }
    }

    fun uploadFile(localUri: Uri) {
        val repository = driveRepository ?: return
        val parentId = _browserState.value.breadcrumbs.last().id
        _browserState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            runCatching { repository.uploadFile(localUri, parentId) }
                .onSuccess { loadCurrentFolder() }
                .onFailure { e ->
                    _browserState.update {
                        it.copy(isBusy = false, error = e.message ?: "Failed to upload file")
                    }
                }
        }
    }

    fun deleteFile(file: DriveFile) {
        val repository = driveRepository ?: return
        _browserState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            runCatching { repository.deleteFile(file.id) }
                .onSuccess { loadCurrentFolder() }
                .onFailure { e ->
                    _browserState.update {
                        it.copy(isBusy = false, error = e.message ?: "Failed to delete ${file.name}")
                    }
                }
        }
    }

    fun downloadAndOpen(file: DriveFile) {
        val repository = driveRepository ?: return
        _browserState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val destination = File(downloadsDir, file.name)
                    repository.downloadFile(file.id, destination)
                    destination
                }
            }
            result.onSuccess { destination ->
                _browserState.update { it.copy(isBusy = false) }
                val context = getApplication<Application>()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                openFileChannel.trySend(intent)
            }.onFailure { e ->
                _browserState.update {
                    it.copy(isBusy = false, error = e.message ?: "Failed to download ${file.name}")
                }
            }
        }
    }

    fun consumeError() {
        _browserState.update { it.copy(error = null) }
    }
}
