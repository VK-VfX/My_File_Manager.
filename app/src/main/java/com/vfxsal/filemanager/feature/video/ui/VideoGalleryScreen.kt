package com.vfxsal.filemanager.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.vfxsal.filemanager.feature.video.VideoGalleryUiState
import com.vfxsal.filemanager.feature.video.data.VideoFolder
import com.vfxsal.filemanager.feature.video.data.VideoItem

private const val TAB_FOLDERS = 0
private const val TAB_ALL_VIDEOS = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGalleryScreen(
    uiState: VideoGalleryUiState,
    imageLoader: ImageLoader,
    onFolderClick: (VideoFolder) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(TAB_FOLDERS) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Video") }) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading && !uiState.hasLoaded -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.hasLoaded && uiState.allVideos.isEmpty() -> {
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
                                onVideoClick = onVideoClick,
                            )
                        }
                    }
                }
            }
        }
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
            )
        }
    }
}

@Composable
private fun VideosGrid(
    videos: List<VideoItem>,
    imageLoader: ImageLoader,
    onVideoClick: (VideoItem) -> Unit,
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
            )
        }
    }
}
