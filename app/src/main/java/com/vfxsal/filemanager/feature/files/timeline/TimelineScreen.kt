package com.vfxsal.filemanager.feature.files.timeline

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader
import kotlinx.coroutines.launch

private const val GRID_COLUMNS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onBack: () -> Unit,
    onEditFile: (String) -> Unit,
    viewModel: TimelineViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val thumbnailLoader = rememberMediaThumbnailLoader()

    LaunchedEffect(Unit) { viewModel.load() }

    val onOpen: (FileEntry) -> Unit = { entry ->
        if (!FileOps.openOrEdit(context, entry, onEditFile)) {
            scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            viewModel.backup(treeUri) { success ->
                scope.launch { snackbarHostState.showSnackbar(if (success) "Backup saved" else "Backup failed") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { backupLauncher.launch(null) },
                        enabled = uiState.monthGroups.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = "Backup timeline")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val state = when {
                uiState.isLoading -> "loading"
                uiState.monthGroups.isEmpty() && uiState.onThisDay.isEmpty() -> "empty"
                else -> "content"
            }
            Crossfade(targetState = state, label = "timelineContent") { s ->
                when (s) {
                    "loading" -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CurlyLoadingIndicator()
                    }
                    "empty" -> EmptyState(message = "No photos or videos found")
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        if (uiState.onThisDay.isNotEmpty()) {
                            item {
                                Text(
                                    text = "On This Day",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    uiState.onThisDay.forEach { group ->
                                        items(group.entries, key = { it.path }) { entry ->
                                            OnThisDayCard(
                                                entry = entry,
                                                yearsAgo = group.yearsAgo,
                                                thumbnailLoader = thumbnailLoader,
                                                onClick = { onOpen(entry) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        uiState.monthGroups.forEach { group ->
                            item(key = "header-${group.label}") {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                            val rows = group.entries.chunked(GRID_COLUMNS)
                            items(rows, key = { row -> row.first().path }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    row.forEach { entry ->
                                        TimelineThumbnail(
                                            entry = entry,
                                            thumbnailLoader = thumbnailLoader,
                                            onClick = { onOpen(entry) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(GRID_COLUMNS - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnThisDayCard(
    entry: FileEntry,
    yearsAgo: Int,
    thumbnailLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(120.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            AsyncImage(
                model = entry.file,
                imageLoader = thumbnailLoader,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
            )
            if (entry.category == FileCategory.VIDEOS) {
                VideoBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
        }
        Spacer(Modifier.padding(top = 2.dp))
        Text(
            text = "$yearsAgo year${if (yearsAgo == 1) "" else "s"} ago",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimelineThumbnail(
    entry: FileEntry,
    thumbnailLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = entry.file,
            imageLoader = thumbnailLoader,
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
        )
        if (entry.category == FileCategory.VIDEOS) {
            VideoBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}
