package com.vfxsal.filemanager.feature.video

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.vfxsal.filemanager.feature.video.data.VideoItem
import com.vfxsal.filemanager.feature.video.ui.VideoFolderScreen
import com.vfxsal.filemanager.feature.video.ui.VideoGalleryScreen
import com.vfxsal.filemanager.feature.video.ui.VideoPermissionRationale
import com.vfxsal.filemanager.feature.video.ui.VideoPlayerScreen
import com.vfxsal.filemanager.feature.video.util.rememberVideoImageLoader
import java.net.URLDecoder
import java.net.URLEncoder

const val VIDEO_GRAPH_ROUTE = "video"

private const val ROUTE_FOLDER = "video/folder/{bucketName}"
private const val ROUTE_PLAYER = "video/player/{encodedUri}?title={title}&folder={folder}"

fun NavGraphBuilder.videoNavGraph(navController: NavHostController) {
    composable(VIDEO_GRAPH_ROUTE) { backStackEntry ->
        val viewModel: VideoGalleryViewModel = viewModel(backStackEntry)
        val context = LocalContext.current

        VideoPermissionGate {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val imageLoader = rememberVideoImageLoader()

            LaunchedEffect(Unit) { viewModel.loadVideos() }

            VideoGalleryScreen(
                uiState = uiState,
                imageLoader = imageLoader,
                onFolderClick = { folder ->
                    navController.navigate("video/folder/${encode(folder.name)}")
                },
                onVideoClick = { video ->
                    viewModel.setQueue(VideoGalleryViewModel.ALL_VIDEOS_QUEUE_KEY, uiState.allVideos)
                    navController.navigate(playerRoute(video, VideoGalleryViewModel.ALL_VIDEOS_QUEUE_KEY))
                },
                onDeleteVideo = { video ->
                    viewModel.deleteVideo(video) { success ->
                        if (!success) Toast.makeText(context, "Could not delete video", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    composable(
        route = ROUTE_FOLDER,
        arguments = listOf(navArgument("bucketName") { type = NavType.StringType }),
    ) { backStackEntry ->
        val bucketName = decode(backStackEntry.arguments?.getString("bucketName").orEmpty())
        val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(VIDEO_GRAPH_ROUTE) }
        val viewModel: VideoGalleryViewModel = viewModel(parentEntry)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val folder = uiState.folders.firstOrNull { it.name == bucketName }
        val context = LocalContext.current

        // Guards against landing here directly after process death, before the gallery screen's
        // own load has ever run for this ViewModel instance.
        LaunchedEffect(Unit) { viewModel.loadVideos() }

        VideoFolderScreen(
            folder = folder,
            imageLoader = rememberVideoImageLoader(),
            onBack = { navController.popBackStack() },
            onVideoClick = { video ->
                viewModel.setQueue(bucketName, folder?.videos.orEmpty())
                navController.navigate(playerRoute(video, bucketName))
            },
            onDeleteVideo = { video ->
                viewModel.deleteVideo(video) { success ->
                    if (!success) Toast.makeText(context, "Could not delete video", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    composable(
        route = ROUTE_PLAYER,
        arguments = listOf(
            navArgument("encodedUri") { type = NavType.StringType },
            navArgument("title") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("folder") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments
        val uri = Uri.parse(decode(args?.getString("encodedUri").orEmpty()))
        val title = decode(args?.getString("title").orEmpty())
        val folderKey = decode(args?.getString("folder").orEmpty())

        val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(VIDEO_GRAPH_ROUTE) }
        val viewModel: VideoGalleryViewModel = viewModel(parentEntry)

        // The stored queue can be empty after process death (it's only kept in memory), so fall
        // back to a single-item queue built from the nav args rather than crashing the player.
        val storedQueue = viewModel.queueFor(folderKey)
        val queue = if (storedQueue.any { it.uri == uri }) {
            storedQueue
        } else {
            listOf(
                VideoItem(
                    id = -1,
                    uri = uri,
                    displayName = title,
                    durationMs = 0L,
                    sizeBytes = 0L,
                    dateModifiedSeconds = 0L,
                    bucketName = folderKey,
                    width = 0,
                    height = 0,
                    relativePath = null,
                ),
            )
        }
        val startIndex = queue.indexOfFirst { it.uri == uri }.coerceAtLeast(0)

        VideoPlayerScreen(
            queue = queue,
            startIndex = startIndex,
            fallbackTitle = title,
            onBack = { navController.popBackStack() },
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun VideoPermissionGate(content: @Composable () -> Unit) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    AnimatedContent(
        targetState = permissionState.status.isGranted,
        transitionSpec = {
            (fadeIn(tween(250)) togetherWith fadeOut(tween(150)))
        },
        label = "videoPermissionGate",
    ) { granted ->
        if (granted) {
            content()
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoPermissionRationale(
                    shouldShowRationale = permissionState.status.shouldShowRationale,
                    onRequestPermission = { permissionState.launchPermissionRequest() },
                )
            }
        }
    }
}

private fun playerRoute(video: VideoItem, folderKey: String): String {
    val encodedUri = encode(video.uri.toString())
    val encodedTitle = encode(video.displayName)
    val encodedFolder = encode(folderKey)
    return "video/player/$encodedUri?title=$encodedTitle&folder=$encodedFolder"
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
