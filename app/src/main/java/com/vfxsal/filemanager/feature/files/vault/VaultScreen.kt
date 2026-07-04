package com.vfxsal.filemanager.feature.files.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkPinStatus() }

    if (!uiState.isUnlocked) {
        VaultUnlockScreen(
            hasPin = uiState.hasPin,
            onBack = onBack,
            onCreatePin = { pin -> viewModel.createPin(pin) },
            onUnlock = { pin, onResult -> viewModel.unlock(pin, onResult) },
        )
    } else {
        VaultContentScreen(
            uiState = uiState,
            onBack = onBack,
            onLock = { viewModel.lock() },
            onRestore = { entry, onResult -> viewModel.restore(entry, onResult) },
            onDeleteForever = { entry, onResult -> viewModel.deleteForever(entry, onResult) },
            onBackup = { treeUri, onResult -> viewModel.backup(treeUri, onResult) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultUnlockScreen(
    hasPin: Boolean,
    onBack: () -> Unit,
    onCreatePin: (String) -> Unit,
    onUnlock: (String, (Boolean) -> Unit) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isCreating = !hasPin

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isCreating) "Create a PIN to secure your vault" else "Enter your PIN",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; error = null } },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (isCreating) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { confirmPin = it; error = null } },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 digits"
                        isCreating && pin != confirmPin -> error = "PINs don't match"
                        isCreating -> onCreatePin(pin)
                        else -> onUnlock(pin) { success ->
                            if (!success) {
                                error = "Incorrect PIN"
                                pin = ""
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isCreating) "Create Vault" else "Unlock")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultContentScreen(
    uiState: VaultUiState,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onRestore: (VaultOps.VaultEntry, (Boolean) -> Unit) -> Unit,
    onDeleteForever: (VaultOps.VaultEntry, (Boolean) -> Unit) -> Unit,
    onBackup: (Uri, (Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val thumbnailLoader = rememberMediaThumbnailLoader()
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            onBackup(treeUri) { success ->
                scope.launch { snackbarHostState.showSnackbar(if (success) "Backup saved" else "Backup failed") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { backupLauncher.launch(null) },
                        enabled = uiState.entries.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = "Backup vault")
                    }
                    IconButton(onClick = onLock) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock vault")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val state = when {
                uiState.isLoading -> "loading"
                uiState.entries.isEmpty() -> "empty"
                else -> "list"
            }
            Crossfade(targetState = state, label = "vaultContent") { s ->
                when (s) {
                    "loading" -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CurlyLoadingIndicator()
                    }
                    "empty" -> EmptyState(
                        message = "Your vault is empty.\nMove files here from the selection menu in the file browser.",
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.entries, key = { it.id }) { entry ->
                            VaultRow(
                                entry = entry,
                                thumbnailLoader = thumbnailLoader,
                                onRestore = {
                                    onRestore(entry) { success ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(if (success) "Restored" else "Could not restore")
                                        }
                                    }
                                },
                                onDeleteForever = {
                                    onDeleteForever(entry) { success ->
                                        if (!success) scope.launch { snackbarHostState.showSnackbar("Could not delete") }
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultRow(
    entry: VaultOps.VaultEntry,
    thumbnailLoader: ImageLoader,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val originalFile = remember(entry.originalPath) { File(entry.originalPath) }
        val category = if (entry.isDirectory) FileCategory.FOLDER else FileCategory.fromExtension(originalFile.extension)
        val showsThumbnail = !entry.isDirectory && (category == FileCategory.IMAGES || category == FileCategory.VIDEOS)
        val vaultedFile = remember(entry.id) { entry.vaultedFile(context) }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(category.color().copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (showsThumbnail) {
                AsyncImage(
                    model = vaultedFile,
                    imageLoader = thumbnailLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(imageVector = category.icon, contentDescription = null, tint = category.color())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = originalFile.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${FormatUtils.formatFileSize(entry.sizeBytes)} • Added ${FormatUtils.formatDate(entry.addedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRestore) {
            Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Restore")
        }
        IconButton(onClick = onDeleteForever) {
            Icon(Icons.Filled.DeleteForever, contentDescription = "Delete forever", tint = MaterialTheme.colorScheme.error)
        }
    }
}
