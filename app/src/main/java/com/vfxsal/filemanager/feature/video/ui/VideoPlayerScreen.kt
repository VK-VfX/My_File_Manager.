package com.vfxsal.filemanager.feature.video.ui

import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vfxsal.filemanager.feature.video.data.VideoItem
import com.vfxsal.filemanager.feature.video.util.findActivity

private const val SWIPE_THRESHOLD_PX = 160f

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

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(exoPlayer, currentVideo?.uri) {
        val video = currentVideo ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
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

        if (queue.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (hasPrevious) currentIndex-- }, enabled = hasPrevious) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous video",
                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                    )
                }
                IconButton(onClick = { if (hasNext) currentIndex++ }, enabled = hasNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next video",
                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}
