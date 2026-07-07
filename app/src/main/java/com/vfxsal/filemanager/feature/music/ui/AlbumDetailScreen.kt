package com.vfxsal.filemanager.feature.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.vfxsal.filemanager.feature.music.ui.components.AlbumArtThumbnail
import com.vfxsal.filemanager.feature.music.ui.components.MiniPlayerBar
import com.vfxsal.filemanager.feature.music.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    viewModel: MusicViewModel = rememberMusicViewModel(),
) {
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val album = library.albums.firstOrNull { it.albumId == albumId }
    val requestNotificationPermission = rememberNotificationPermissionRequester()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.title ?: "Album", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        if (album == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Album not found")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        AlbumArtThumbnail(model = album.albumArtUri, size = 96.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(album.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                album.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val songWord = if (album.tracks.size == 1) "song" else "songs"
                            Text(
                                "${album.tracks.size} $songWord",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(album.tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isActive = track.id.toString() == playback.mediaItem?.mediaId,
                        onClick = {
                            requestNotificationPermission()
                            viewModel.playQueue(album.tracks, track)
                        },
                    )
                }
            }
        }
    }
}
