package com.vfxsal.filemanager.feature.music.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vfxsal.filemanager.feature.music.MusicViewModel
import com.vfxsal.filemanager.feature.music.data.AlbumSummary
import com.vfxsal.filemanager.feature.music.data.ArtistSummary
import com.vfxsal.filemanager.feature.music.data.Track
import com.vfxsal.filemanager.feature.music.rememberMusicViewModel
import com.vfxsal.filemanager.feature.music.ui.components.AlbumArtThumbnail
import com.vfxsal.filemanager.feature.music.ui.components.MiniPlayerBar
import com.vfxsal.filemanager.feature.music.ui.components.TrackRow
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator

private enum class MusicTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: MusicViewModel = rememberMusicViewModel(),
) {
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(audioPermission)
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            viewModel.loadLibraryIfNeeded()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Music") }) },
        bottomBar = {
            AnimatedVisibility(
                visible = playback.mediaItem != null,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
                MiniPlayerBar(
                    playback = playback,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onClick = onNavigateToNowPlaying,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            val libraryContentState = when {
                !permissionState.status.isGranted -> "permission"
                library.isLoading && !library.hasLoadedOnce -> "loading"
                library.tracks.isEmpty() -> "empty"
                else -> "content"
            }
            Crossfade(targetState = libraryContentState, label = "musicLibraryContent") { state ->
                when (state) {
                    "permission" -> PermissionRequiredContent(
                        onGrantClick = { permissionState.launchPermissionRequest() },
                    )
                    "loading" -> LoadingContent()
                    "empty" -> EmptyLibraryContent()
                    else -> Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            MusicTab.entries.forEachIndexed { index, tab ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(tab.label) },
                                )
                            }
                        }
                        when (MusicTab.entries[selectedTab]) {
                            MusicTab.SONGS -> SongsTab(
                                tracks = library.tracks,
                                currentMediaId = playback.mediaItem?.mediaId,
                                onTrackClick = { track ->
                                    requestNotificationPermission()
                                    viewModel.playQueue(library.tracks, track)
                                },
                            )
                            MusicTab.ALBUMS -> AlbumsTab(
                                albums = library.albums,
                                onAlbumClick = { onNavigateToAlbum(it.albumId) },
                            )
                            MusicTab.ARTISTS -> ArtistsTab(
                                artists = library.artists,
                                onArtistClick = { onNavigateToArtist(it.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongsTab(
    tracks: List<Track>,
    currentMediaId: String?,
    onTrackClick: (Track) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isActive = track.id.toString() == currentMediaId,
                onClick = { onTrackClick(track) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<AlbumSummary>, onAlbumClick: (AlbumSummary) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.albumId }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AlbumArtThumbnail(
                model = album.albumArtUri,
                size = 160.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistsTab(artists: List<ArtistSummary>, onArtistClick: (ArtistSummary) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            ArtistRow(artist = artist, onClick = { onArtistClick(artist) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(artist.name) },
        supportingContent = {
            val albumWord = if (artist.albumCount == 1) "album" else "albums"
            val songWord = if (artist.tracks.size == 1) "song" else "songs"
            Text("${artist.albumCount} $albumWord - ${artist.tracks.size} $songWord")
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun PermissionRequiredContent(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "WhatFiles? needs permission to read your music library.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrantClick) { Text("Grant access") }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CurlyLoadingIndicator()
    }
}

@Composable
private fun EmptyLibraryContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No music found on this device.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
