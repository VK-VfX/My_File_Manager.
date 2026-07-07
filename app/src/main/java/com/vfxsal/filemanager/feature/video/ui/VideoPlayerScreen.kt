package com.vfxsal.filemanager.feature.video.ui

import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vfxsal.filemanager.feature.video.data.VideoItem
import com.vfxsal.filemanager.feature.video.util.findActivity
import com.vfxsal.filemanager.util.FormatUtils
import kotlinx.coroutines.delay

private const val SWIPE_THRESHOLD_PX = 160f
private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_AUTO_HIDE_MS = 4_000L
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun VideoPlayerScreen(
    queue: List<VideoItem>,
    startIndex: Int,
    fallbackTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var currentIndex by rememberSaveable {
        mutableIntStateOf(startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
    }
    val currentVideo = queue.getOrNull(currentIndex)

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Media3 only pushes discrete change events, never continuous position updates, so poll
    // while this screen is composed; skip updates while the user is actively dragging the
    // seek bar so the ticker doesn't fight their gesture.
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isUserSeeking) currentPositionMs = exoPlayer.currentPosition
            delay(500)
        }
    }

    LaunchedEffect(exoPlayer, currentVideo?.uri) {
        val video = currentVideo ?: return@LaunchedEffect
        currentPositionMs = 0L
        durationMs = 0L
        exoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Auto-hide the control bar a few seconds into playback; tapping the video toggles it.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // Immersive playback: hide system bars and keep the screen on only while this screen is
    // composed, restoring both on dispose so the rest of the app is unaffected.
    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(onBack = onBack)

    val hasPrevious = currentIndex > 0
    val hasNext = currentIndex < queue.lastIndex
    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(currentIndex, queue.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccumulated <= -SWIPE_THRESHOLD_PX && hasNext) {
                            currentIndex++
                        } else if (dragAccumulated >= SWIPE_THRESHOLD_PX && hasPrevious) {
                            currentIndex--
                        }
                        dragAccumulated = 0f
                    },
                    onDragCancel = { dragAccumulated = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        dragAccumulated += dragAmount
                        change.consume()
                    },
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)),
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = currentVideo?.displayName ?: fallbackTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            BottomControlBar(
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                playbackSpeed = playbackSpeed,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                onScrub = { positionMs ->
                    isUserSeeking = true
                    currentPositionMs = positionMs
                },
                onScrubFinished = {
                    exoPlayer.seekTo(currentPositionMs)
                    isUserSeeking = false
                },
                onTogglePlayPause = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onRewind = {
                    exoPlayer.seekTo((exoPlayer.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
                },
                onFastForward = {
                    val target = exoPlayer.currentPosition + SEEK_STEP_MS
                    exoPlayer.seekTo(if (durationMs > 0) target.coerceAtMost(durationMs) else target)
                },
                onPrevious = { if (hasPrevious) currentIndex-- },
                onNext = { if (hasNext) currentIndex++ },
                onCycleSpeed = {
                    val nextIndex = (PLAYBACK_SPEEDS.indexOf(playbackSpeed) + 1).let { if (it >= PLAYBACK_SPEEDS.size) 0 else it }
                    playbackSpeed = PLAYBACK_SPEEDS[nextIndex]
                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                },
            )
        }
    }
}

@Composable
private fun BottomControlBar(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))),
            )
            .padding(top = 24.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = FormatUtils.formatDuration(currentPositionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Slider(
                value = currentPositionMs.toFloat(),
                onValueChange = { onScrub(it.toLong()) },
                onValueChangeFinished = onScrubFinished,
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                text = FormatUtils.formatDuration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous video",
                    tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }
            IconButton(onClick = onRewind) {
                Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White)
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onFastForward) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White)
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next video",
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }
            SpeedControl(playbackSpeed = playbackSpeed, onClick = onCycleSpeed)
        }
    }
}

@Composable
private fun SpeedControl(playbackSpeed: Float, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Speed,
            contentDescription = "Playback speed",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "${if (playbackSpeed == playbackSpeed.toInt().toFloat()) playbackSpeed.toInt().toString() else playbackSpeed.toString()}x",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
