package com.vfxsal.filemanager.feature.cloud.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vfxsal.filemanager.feature.cloud.PhotosImportViewModel
import com.vfxsal.filemanager.feature.cloud.PickedMediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosImportScreen(
    onBack: () -> Unit,
    viewModel: PhotosImportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> viewModel.onItemsPicked(uris) }

    LaunchedEffect(uiState.importedCount) {
        val count = uiState.importedCount
        if (count != null) {
            snackbarHostState.showSnackbar(
                if (count == 1) "Imported 1 item" else "Imported $count items",
            )
            viewModel.clearImportResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.pickedItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = "Browse Google Photos",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    Text(
                        text = "Uses the system Photos picker, which surfaces your cloud-backed " +
                            "Google Photos library directly — no sign-in needed here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    Button(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                ),
                            )
                        },
                    ) {
                        Text("Browse Google Photos")
                    }
                }
            } else {
                val selectedCount = uiState.pickedItems.count { it.selected }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    items(uiState.pickedItems, key = { it.uri }) { item ->
                        MediaGridCell(
                            item = item,
                            enabled = !uiState.isImporting,
                            onToggle = { viewModel.toggleSelection(item.uri) },
                        )
                    }
                }

                if (uiState.isImporting) {
                    val progress = if (uiState.importTotal > 0) {
                        uiState.importProgress / uiState.importTotal.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "Importing ${uiState.importProgress} of ${uiState.importTotal}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                ),
                            )
                        },
                        enabled = !uiState.isImporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Pick more")
                    }
                    Button(
                        onClick = { viewModel.importSelected() },
                        enabled = selectedCount > 0 && !uiState.isImporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Import $selectedCount item${if (selectedCount == 1) "" else "s"}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGridCell(
    item: PickedMediaItem,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
            )
        }
        Checkbox(
            checked = item.selected,
            onCheckedChange = { if (enabled) onToggle() },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
