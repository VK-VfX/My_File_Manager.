package com.vfxsal.filemanager.feature.clean.duplicates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.clean.model.DuplicateGroup
import com.vfxsal.filemanager.feature.clean.model.DuplicateScanPhase
import com.vfxsal.filemanager.feature.clean.ui.CleanBottomBar
import com.vfxsal.filemanager.feature.clean.ui.DeleteConfirmationDialog
import com.vfxsal.filemanager.feature.clean.ui.EmptyResultsMessage
import com.vfxsal.filemanager.feature.clean.ui.GroupHeader
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFilesScreen(
    onBack: () -> Unit,
    viewModel: DuplicateFilesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val thumbnailLoader = rememberMediaThumbnailLoader()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::scan, enabled = !uiState.isScanning) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.groups.isNotEmpty(),
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
                CleanBottomBar(
                    selectedCount = uiState.selectedCount,
                    selectedBytes = uiState.selectedBytes,
                    enabled = !uiState.isDeleting,
                    onCleanClick = { showConfirm = true },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isScanning && uiState.groups.isNotEmpty(),
            onRefresh = viewModel::scan,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val duplicatesContentState = when {
                uiState.isScanning && uiState.groups.isEmpty() -> "scanning"
                uiState.groups.isEmpty() -> "empty"
                else -> "list"
            }
            Crossfade(targetState = duplicatesContentState, label = "duplicateFilesContent") { state ->
                when (state) {
                    "scanning" -> {
                        val phaseLabel = if (uiState.phase == DuplicateScanPhase.SIZE_BUCKETING) {
                            "Comparing file sizes"
                        } else {
                            "Verifying duplicates"
                        }
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("$phaseLabel… ${uiState.progressCount}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    "empty" ->
                        EmptyResultsMessage("No duplicate files found.", Modifier.fillMaxSize())
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        uiState.groups.forEach { group ->
                            item(key = "header-${group.hash}") {
                                GroupHeader(
                                    title = "${group.files.size} copies · ${FormatUtils.formatFileSize(group.sizeBytes)} each",
                                    sizeText = "${FormatUtils.formatFileSize(group.wastedBytes)} wasted",
                                )
                            }
                            val keepPath = uiState.keepers[group.hash]
                            items(group.files, key = { it.path }) { entry ->
                                DuplicateFileRow(
                                    entry = entry,
                                    isKeeper = entry.path == keepPath,
                                    checked = entry.path in uiState.selectedPaths,
                                    onToggle = { viewModel.toggleSelection(entry.path) },
                                    onKeepClick = { viewModel.setKeeper(group, entry.path) },
                                    thumbnailLoader = thumbnailLoader,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "divider-${group.hash}") {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        DeleteConfirmationDialog(
            itemCount = uiState.selectedCount,
            totalBytes = uiState.selectedBytes,
            onConfirm = {
                showConfirm = false
                viewModel.deleteSelected()
            },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun DuplicateFileRow(
    entry: FileEntry,
    isKeeper: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onKeepClick: () -> Unit,
    thumbnailLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = !isKeeper)
        if (entry.category == FileCategory.IMAGES || entry.category == FileCategory.VIDEOS) {
            AsyncImage(
                model = entry.file,
                imageLoader = thumbnailLoader,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Icon(
                imageVector = entry.category.icon,
                contentDescription = null,
                tint = entry.category.color(),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.path,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = FormatUtils.formatDate(entry.lastModified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isKeeper) {
            AssistChip(onClick = {}, enabled = false, label = { Text("Keeping") })
        } else {
            TextButton(onClick = onKeepClick) { Text("Keep") }
        }
    }
}
