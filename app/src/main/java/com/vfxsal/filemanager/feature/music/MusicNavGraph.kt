package com.vfxsal.filemanager.feature.music

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vfxsal.filemanager.feature.music.ui.AlbumDetailScreen
import com.vfxsal.filemanager.feature.music.ui.ArtistDetailScreen
import com.vfxsal.filemanager.feature.music.ui.MusicLibraryScreen
import com.vfxsal.filemanager.feature.music.ui.NowPlayingScreen

const val MUSIC_GRAPH_ROUTE = "music"

private const val NOW_PLAYING_ROUTE = "music/nowplaying"
private const val ALBUM_DETAIL_ROUTE = "music/album/{albumId}"
private const val ARTIST_DETAIL_ROUTE = "music/artist/{artistName}"
private const val ALBUM_ID_ARG = "albumId"
private const val ARTIST_NAME_ARG = "artistName"

fun NavGraphBuilder.musicNavGraph(navController: NavHostController) {
    composable(MUSIC_GRAPH_ROUTE) {
        MusicLibraryScreen(
            onNavigateToAlbum = { albumId -> navController.navigate("music/album/$albumId") },
            onNavigateToArtist = { artistName ->
                navController.navigate("music/artist/${Uri.encode(artistName)}")
            },
            onNavigateToNowPlaying = { navController.navigate(NOW_PLAYING_ROUTE) },
        )
    }

    composable(
        route = ALBUM_DETAIL_ROUTE,
        arguments = listOf(navArgument(ALBUM_ID_ARG) { type = NavType.LongType }),
    ) { backStackEntry ->
        val albumId = backStackEntry.arguments?.getLong(ALBUM_ID_ARG) ?: 0L
        AlbumDetailScreen(
            albumId = albumId,
            onBack = { navController.popBackStack() },
            onNavigateToNowPlaying = { navController.navigate(NOW_PLAYING_ROUTE) },
        )
    }

    composable(
        route = ARTIST_DETAIL_ROUTE,
        arguments = listOf(navArgument(ARTIST_NAME_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        val artistName = backStackEntry.arguments?.getString(ARTIST_NAME_ARG)?.let(Uri::decode).orEmpty()
        ArtistDetailScreen(
            artistName = artistName,
            onBack = { navController.popBackStack() },
            onNavigateToNowPlaying = { navController.navigate(NOW_PLAYING_ROUTE) },
        )
    }

    composable(NOW_PLAYING_ROUTE) {
        NowPlayingScreen(onBack = { navController.popBackStack() })
    }
}
