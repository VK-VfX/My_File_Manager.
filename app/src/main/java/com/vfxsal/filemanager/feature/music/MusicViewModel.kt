package com.vfxsal.filemanager.feature.music

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.feature.music.data.AlbumSummary
import com.vfxsal.filemanager.feature.music.data.ArtistSummary
import com.vfxsal.filemanager.feature.music.data.MusicRepository
import com.vfxsal.filemanager.feature.music.data.Track
import com.vfxsal.filemanager.feature.music.data.toMediaItem
import com.vfxsal.filemanager.feature.music.player.MusicController
import com.vfxsal.filemanager.feature.music.player.MusicPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicLibraryUiState(
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
)

/**
 * Scoped to the hosting Activity (see [rememberMusicViewModel]) rather than to a single nav
 * destination, so the same [MusicController] connection and library cache survive navigating
 * between the library, album/artist detail, and now-playing routes within the Music tab.
 */
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    val controller = MusicController(application)
    val playback: StateFlow<MusicPlaybackState> = controller.state

    private val _library = MutableStateFlow(MusicLibraryUiState())
    val library: StateFlow<MusicLibraryUiState> = _library.asStateFlow()

    init {
        controller.connect()
    }

    fun loadLibraryIfNeeded() {
        if (_library.value.hasLoadedOnce) return
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _library.update { it.copy(isLoading = true) }
            val tracks = repository.loadTracks()
            _library.value = MusicLibraryUiState(
                isLoading = false,
                hasLoadedOnce = true,
                tracks = tracks,
                albums = repository.groupByAlbum(tracks),
                artists = repository.groupByArtist(tracks),
            )
        }
    }

    fun playQueue(queue: List<Track>, startTrack: Track) {
        val startIndex = queue.indexOfFirst { it.id == startTrack.id }.coerceAtLeast(0)
        controller.playQueue(queue.map { it.toMediaItem() }, startIndex)
    }

    fun togglePlayPause() = controller.togglePlayPause()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun skipToNext() = controller.skipToNext()

    fun skipToPrevious() = controller.skipToPrevious()

    fun toggleShuffle() = controller.setShuffleModeEnabled(!playback.value.shuffleModeEnabled)

    fun cycleRepeatMode() = controller.cycleRepeatMode()

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}

/**
 * Scopes [MusicViewModel] to the hosting Activity instead of the current nav-back-stack-entry
 * (the default `viewModel()` owner), since the music library screen and the now-playing screen
 * are sibling top-level destinations in the app's single NavHost rather than entries within one
 * nested nav graph, and must still share one live player connection.
 */
@Composable
fun rememberMusicViewModel(): MusicViewModel {
    val activity = LocalContext.current.findActivity()
    return viewModel(viewModelStoreOwner = activity)
}

private fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Expected a ComponentActivity context")
}
