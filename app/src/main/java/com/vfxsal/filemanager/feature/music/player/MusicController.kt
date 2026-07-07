package com.vfxsal.filemanager.feature.music.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vfxsal.filemanager.service.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MusicPlaybackState(
    val mediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isConnected: Boolean = false,
)

/**
 * Connects to [MusicPlaybackService] as a [MediaController] and republishes its state as a
 * [StateFlow] that Compose screens can collect. Media3 only pushes discrete change events
 * (play/pause, item transition, ...); it never streams continuous position updates, so while
 * something is playing we poll `controller.currentPosition` on a 500ms ticker to drive the
 * now-playing progress bar smoothly.
 */
class MusicController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker() else stopTicker()
            refreshFromController()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            refreshFromController()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshFromController()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshFromController()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            refreshFromController()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            refreshFromController()
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val sessionToken = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    refreshFromController()
                    if (mediaController.isPlaying) startTicker()
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        stopTicker()
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _state.value = MusicPlaybackState()
    }

    fun playQueue(items: List<MediaItem>, startIndex: Int, startPositionMs: Long = 0L) {
        val c = controller ?: return
        c.setMediaItems(items, startIndex, startPositionMs)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        refreshFromController()
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        refreshFromController()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                refreshPositionOnly()
                delay(500L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun refreshFromController() {
        val c = controller ?: return
        _state.update {
            it.copy(
                mediaItem = c.currentMediaItem,
                isPlaying = c.isPlaying,
                playbackState = c.playbackState,
                currentPositionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
                shuffleModeEnabled = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                isConnected = true,
            )
        }
    }

    private fun refreshPositionOnly() {
        val c = controller ?: return
        _state.update {
            it.copy(
                currentPositionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
            )
        }
    }
}
