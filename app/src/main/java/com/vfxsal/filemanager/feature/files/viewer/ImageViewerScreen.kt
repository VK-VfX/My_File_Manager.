package com.vfxsal.filemanager.feature.files.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.browse.SortBy
import com.vfxsal.filemanager.feature.files.components.DeleteConfirmDialog
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    startPath: String,
    source: ImageViewerSource,
    sortBy: SortBy,
    ascending: Boolean,
    onBack: () -> Unit,
    viewModel: ImageViewerViewModel = viewModel(),
) {
    LaunchedEffect(startPath, source, sortBy, ascending) { viewModel.load(startPath, source, sortBy, ascending) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var infoTarget by remember { mutableStateOf<FileEntry?>(null) }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
                uiState.images.isEmpty() -> {
                    Text(
                        text = "Image not found",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    LaunchedEffect(Unit) { onBack() }
                }
                else -> ImageViewerPager(
                    images = uiState.images,
                    initialIndex = uiState.initialIndex,
                    onBack = onBack,
                    onShare = { entry ->
                        if (!FileOps.tryShare(context, listOf(entry.file))) {
                            scope.launch { snackbarHostState.showSnackbar("Unable to share this image") }
                        }
                    },
                    onDeleteRequest = { entry -> deleteTarget = entry },
                    onInfoRequest = { entry -> infoTarget = entry },
                )
            }
        }
    }

    deleteTarget?.let { entry ->
        DeleteConfirmDialog(
            count = 1,
            onDismiss = { deleteTarget = null },
            onConfirm = { permanent ->
                deleteTarget = null
                val wasLast = uiState.images.size <= 1
                viewModel.deleteAndRemove(entry, permanent) { success ->
                    scope.launch {
                        val message = if (success) {
                            if (permanent) "Deleted permanently" else "Moved to trash"
                        } else {
                            "Could not delete"
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                    if (success && wasLast) onBack()
                }
            },
        )
    }

    infoTarget?.let { entry ->
        ImageInfoDialog(entry = entry, onDismiss = { infoTarget = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageViewerPager(
    images: List<FileEntry>,
    initialIndex: Int,
    onBack: () -> Unit,
    onShare: (FileEntry) -> Unit,
    onDeleteRequest: (FileEntry) -> Unit,
    onInfoRequest: (FileEntry) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, images.lastIndex)) { images.size }
    var isZoomed by remember { mutableStateOf(false) }
    var dismissDragY by remember { mutableStateOf(0f) }

    LaunchedEffect(pagerState.currentPage) { isZoomed = false }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier
                .fillMaxSize()
                // Swipe down on an unzoomed photo to dismiss the viewer, like most galleries.
                .pointerInput(isZoomed) {
                    if (!isZoomed) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                val next = (dismissDragY + dragAmount).coerceAtLeast(0f)
                                if (next > 0f) {
                                    dismissDragY = next
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (dismissDragY > 350f) onBack() else dismissDragY = 0f
                            },
                            onDragCancel = { dismissDragY = 0f },
                        )
                    }
                }
                .graphicsLayer {
                    translationY = dismissDragY
                    alpha = 1f - (dismissDragY / 1500f).coerceIn(0f, 0.5f)
                },
        ) { page ->
            ZoomableImage(
                entry = images[page],
                onZoomChanged = { zoomed -> if (page == pagerState.currentPage) isZoomed = zoomed },
            )
        }

        val current = images[pagerState.currentPage]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (images.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} of ${images.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = { onShare(current) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share image", tint = Color.White)
            }
            IconButton(onClick = { onInfoRequest(current) }) {
                Icon(Icons.Filled.Info, contentDescription = "Image details", tint = Color.White)
            }
            IconButton(onClick = { onDeleteRequest(current) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete image", tint = Color.White)
            }
        }
    }
}

/**
 * Pinch-to-zoom, drag-to-pan while zoomed, and double-tap to toggle zoom. Gestures are
 * handled with a manual pointer loop instead of detectTransformGestures because that
 * detector consumes single-finger drags too - which would steal horizontal swipes from
 * the pager and vertical swipes from the dismiss gesture. Here events are only consumed
 * while actually pinching (2+ pointers) or panning a zoomed-in photo.
 */
@Composable
private fun ZoomableImage(entry: FileEntry, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(entry.path) { mutableStateOf(1f) }
    var offset by remember(entry.path) { mutableStateOf(Offset.Zero) }
    val thumbnailLoader = rememberMediaThumbnailLoader()

    AsyncImage(
        model = entry.file,
        imageLoader = thumbnailLoader,
        contentDescription = entry.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(entry.path) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                        onZoomChanged(scale > 1f)
                    },
                )
            }
            .pointerInput(entry.path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            scale = (scale * zoomChange).coerceIn(1f, 6f)
                            offset = if (scale <= 1f) Offset.Zero else offset + panChange
                            onZoomChanged(scale > 1f)
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            val panChange = event.calculatePan()
                            offset += panChange
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
    )
}

@Composable
private fun ImageInfoDialog(entry: FileEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column {
                InfoRow(label = "Location", value = entry.file.parent ?: entry.path)
                InfoRow(label = "Size", value = FormatUtils.formatFileSize(entry.sizeBytes))
                InfoRow(label = "Modified", value = FormatUtils.formatDateTime(entry.lastModified))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
