package com.vfxsal.filemanager.feature.video.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.vfxsal.filemanager.feature.video.data.VideoFolder
import com.vfxsal.filemanager.feature.video.data.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFolderScreen(
    folder: VideoFolder?,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: (List<VideoItem>) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: (List<VideoItem>, (Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val videos = folder?.videos.orEmpty()

    BackHandler(enabled = selectionMode) { onClearSelection() }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectionMode) {
                VideoSelectionTopBar(
                    selectedCount = selectedIds.size,
                    onClear = onClearSelection,
                    onSelectAll = { onSelectAll(videos) },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = folder?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
                VideoSelectionBottomBar(
                    onShare = {
                        val uris = videos.filter { it.id in selectedIds }.map { it.uri }
                        shareVideos(context, uris)
                    },
                    onDelete = { showDeleteSelectedDialog = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (videos.isEmpty()) {
                Text(
                    text = "No videos in this folder",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(videos, key = { it.id }) { video ->
                        VideoGridTile(
                            video = video,
                            imageLoader = imageLoader,
                            onClick = {
                                if (selectionMode) onToggleSelection(video.id) else onVideoClick(video)
                            },
                            onLongClick = {
                                if (selectionMode) onToggleSelection(video.id) else onEnterSelectionMode(video.id)
                            },
                            selectionMode = selectionMode,
                            selected = video.id in selectedIds,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete ${selectedIds.size} video(s)?") },
            text = { Text("These videos will be permanently deleted from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedDialog = false
                    onDeleteSelected(videos) { count ->
                        scope.launch { snackbarHostState.showSnackbar("Deleted $count video(s)") }
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Cancel") }
            },
        )
    }
}
