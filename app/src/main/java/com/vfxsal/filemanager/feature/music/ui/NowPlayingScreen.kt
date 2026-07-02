package com.vfxsal.filemanager.feature.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.vfxsal.filemanager.feature.music.MusicViewModel
import com.vfxsal.filemanager.feature.music.rememberMusicViewModel
import com.vfxsal.filemanager.feature.music.ui.components.AlbumArtThumbnail
import com.vfxsal.filemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    viewModel: MusicViewModel = rememberMusicViewModel(),
) {
    val playback by viewModel.playback.collectAsState()
    val mediaItem = playback.mediaItem
    val durationMs = playback.durationMs.coerceAtLeast(0L)

    // Player.Listener only fires on discrete events, so `playback.currentPositionMs` jumps
    // every ~500ms from the controller's polling ticker. While the user is dragging the slider
    // we must render their drag position instead, otherwise the next ticker tick yanks the
    // thumb back to the pre-drag position mid-gesture.
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }
    val displayedPositionMs = if (isUserSeeking) seekPositionMs else playback.currentPositionMs.toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Collapse")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            AlbumArtThumbnail(
                model = mediaItem?.mediaMetadata?.artworkUri,
                size = 280.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = mediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = mediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(24.dp))

            Slider(
                value = displayedPositionMs.coerceIn(0f, durationMs.toFloat().coerceAtLeast(0f)),
                onValueChange = {
                    isUserSeeking = true
                    seekPositionMs = it
                },
                onValueChangeFinished = {
                    viewModel.seekTo(seekPositionMs.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = FormatUtils.formatDuration(displayedPositionMs.toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = FormatUtils.formatDuration(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playback.shuffleModeEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = viewModel::skipToPrevious) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp),
                    )
                }
                FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = viewModel::skipToNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = viewModel::cycleRepeatMode) {
                    Icon(
                        imageVector = if (playback.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (playback.repeatMode != Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
