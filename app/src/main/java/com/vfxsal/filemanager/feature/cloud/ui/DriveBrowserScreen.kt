package com.vfxsal.filemanager.feature.cloud.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vfxsal.filemanager.feature.cloud.CloudViewModel
import com.vfxsal.filemanager.feature.cloud.DriveFolderRef
import com.vfxsal.filemanager.feature.cloud.data.DriveFile
import com.vfxsal.filemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBrowserScreen(
    viewModel: CloudViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.browserState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var fabMenuExpanded by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DriveFile?>(null) }

    BackHandler { if (!viewModel.navigateUp()) onBack() }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::uploadFile) }

    LaunchedEffect(Unit) {
        viewModel.openFileEvents.collect { intent ->
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                snackbarHostState.showSnackbar("No app found to open this file")
            }
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Drive") },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.navigateUp()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { fabMenuExpanded = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
                DropdownMenu(expanded = fabMenuExpanded, onDismissRequest = { fabMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Upload file") },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                        onClick = {
                            fabMenuExpanded = false
                            uploadLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            fabMenuExpanded = false
                            showNewFolderDialog = true
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            BreadcrumbRow(
                breadcrumbs = state.breadcrumbs,
                onCrumbClick = { index -> viewModel.navigateToBreadcrumb(index) },
            )

            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    state.items.isEmpty() -> {
                        Text(
                            text = "This folder is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }

                    else -> {
                        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(state.items, key = { it.id }) { file ->
                                DriveFileRow(
                                    file = file,
                                    onClick = {
                                        if (file.isFolder) {
                                            viewModel.openFolder(file)
                                        } else {
                                            viewModel.downloadAndOpen(file)
                                        }
                                    },
                                    onDeleteRequest = { pendingDelete = file },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name)
            },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${toDelete.name}\"?") },
            text = { Text("This will move the item to your Google Drive trash.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(toDelete)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BreadcrumbRow(
    breadcrumbs: List<DriveFolderRef>,
    onCrumbClick: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(breadcrumbs, key = { index, crumb -> "${crumb.id}_$index" }) { index, crumb ->
            val isLast = index == breadcrumbs.lastIndex
            TextButton(onClick = { onCrumbClick(index) }, enabled = !isLast) {
                Text(
                    text = crumb.name,
                    style = if (isLast) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                )
            }
            if (!isLast) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DriveFileRow(
    file: DriveFile,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (file.isFolder) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onClick() },
        ) {
            Text(text = file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (!file.isFolder) {
                val sizeText = file.sizeBytes?.let { FormatUtils.formatFileSize(it) } ?: "--"
                val dateText = file.modifiedTimeMillis?.let { FormatUtils.formatDate(it) } ?: ""
                Text(
                    text = if (dateText.isNotEmpty()) "$sizeText · $dateText" else sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Folder name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
