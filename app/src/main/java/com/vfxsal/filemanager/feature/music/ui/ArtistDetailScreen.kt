package com.vfxsal.filemanager.feature.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vfxsal.filemanager.feature.music.MusicViewModel
import com.vfxsal.filemanager.feature.music.rememberMusicViewModel
import com.vfxsal.filemanager.feature.music.ui.components.MiniPlayerBar
import com.vfxsal.filemanager.feature.music.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: MusicViewModel = rememberMusicViewModel(),
) {
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val artist = library.artists.firstOrNull { it.name == artistName }
    val requestNotificationPermission = rememberNotificationPermissionRequester()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artist?.name ?: artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (playback.mediaItem != null) {
                MiniPlayerBar(
                    playback = playback,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onClick = onNavigateToNowPlaying,
                )
            }
        },
    ) { innerPadding ->
        if (artist == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Artist not found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val albumWord = if (artist.albumCount == 1) "album" else "albums"
                        val songWord = if (artist.tracks.size == 1) "song" else "songs"
                        Text(
                            "${artist.albumCount} $albumWord - ${artist.tracks.size} $songWord",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(artist.tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isActive = track.id.toString() == playback.mediaItem?.mediaId,
                        onClick = {
                            requestNotificationPermission()
                            viewModel.playQueue(artist.tracks, track)
                        },
                    )
                }
            }
        }
    }
}
