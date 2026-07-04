package com.vfxsal.filemanager.feature.video.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.vfxsal.filemanager.feature.video.VideoGalleryUiState
import com.vfxsal.filemanager.feature.video.data.VideoFolder
import com.vfxsal.filemanager.feature.video.data.VideoItem
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator
import kotlinx.coroutines.launch

private const val TAB_FOLDERS = 0
private const val TAB_ALL_VIDEOS = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGalleryScreen(
    uiState: VideoGalleryUiState,
    imageLoader: ImageLoader,
    onFolderClick: (VideoFolder) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: (List<VideoItem>) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: (List<VideoItem>, (Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(TAB_FOLDERS) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = uiState.selectionMode) { onClearSelection() }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (uiState.selectionMode) {
                VideoSelectionTopBar(
                    selectedCount = uiState.selectedIds.size,
                    onClear = onClearSelection,
                    onSelectAll = { onSelectAll(uiState.allVideos) },
                )
            } else {
                TopAppBar(title = { Text("Video") })
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.selectionMode,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
                VideoSelectionBottomBar(
                    onShare = {
                        val uris = uiState.allVideos.filter { it.id in uiState.selectedIds }.map { it.uri }
                        shareVideos(context, uris)
                    },
                    onDelete = { showDeleteSelectedDialog = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val galleryContentState = when {
                uiState.isLoading && !uiState.hasLoaded -> "loading"
                uiState.hasLoaded && uiState.allVideos.isEmpty() -> "empty"
                else -> "content"
            }
            Crossfade(targetState = galleryContentState, label = "videoGalleryContent") { state ->
                when (state) {
                    "loading" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CurlyLoadingIndicator()
                        }
                    }
                    "empty" -> {
                        Text(
                            text = "No videos found on this device",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == TAB_FOLDERS,
                                    onClick = { selectedTab = TAB_FOLDERS },
                                    text = { Text("Folders") },
                                )
                                Tab(
                                    selected = selectedTab == TAB_ALL_VIDEOS,
                                    onClick = { selectedTab = TAB_ALL_VIDEOS },
                                    text = { Text("All videos") },
                                )
                            }
                            when (selectedTab) {
                                TAB_FOLDERS -> FoldersGrid(
                                    folders = uiState.folders,
                                    imageLoader = imageLoader,
                                    onFolderClick = onFolderClick,
                                )
                                else -> VideosGrid(
                                    videos = uiState.allVideos,
                                    imageLoader = imageLoader,
                                    selectionMode = uiState.selectionMode,
                                    selectedIds = uiState.selectedIds,
                                    onVideoClick = { video ->
                                        if (uiState.selectionMode) {
                                            onToggleSelection(video.id)
                                        } else {
                                            onVideoClick(video)
                                        }
                                    },
                                    onVideoLongClick = { video ->
                                        if (uiState.selectionMode) {
                                            onToggleSelection(video.id)
                                        } else {
                                            onEnterSelectionMode(video.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete ${uiState.selectedIds.size} video(s)?") },
            text = { Text("These videos will be permanently deleted from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedDialog = false
                    onDeleteSelected(uiState.allVideos) { count ->
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

@Composable
private fun FoldersGrid(
    folders: List<VideoFolder>,
    imageLoader: ImageLoader,
    onFolderClick: (VideoFolder) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(folders, key = { it.name }) { folder ->
            VideoFolderTile(
                folder = folder,
                imageLoader = imageLoader,
                onClick = { onFolderClick(folder) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun VideosGrid(
    videos: List<VideoItem>,
    imageLoader: ImageLoader,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongClick: (VideoItem) -> Unit,
) {
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
                onClick = { onVideoClick(video) },
                onLongClick = { onVideoLongClick(video) },
                selectionMode = selectionMode,
                selected = video.id in selectedIds,
                modifier = Modifier.animateItem(),
            )
        }
    }
}
