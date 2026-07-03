package com.vfxsal.filemanager.feature.clean.junk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.clean.ui.CleanBottomBar
import com.vfxsal.filemanager.feature.clean.ui.DeleteConfirmationDialog
import com.vfxsal.filemanager.feature.clean.ui.EmptyResultsMessage
import com.vfxsal.filemanager.feature.clean.ui.GroupHeader
import com.vfxsal.filemanager.feature.clean.ui.ScanningIndicator
import com.vfxsal.filemanager.feature.clean.ui.SelectableFileRow
import com.vfxsal.filemanager.feature.clean.ui.icon
import com.vfxsal.filemanager.feature.clean.ui.label
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunkFilesScreen(
    onBack: () -> Unit,
    viewModel: JunkFilesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val thumbnailLoader = rememberMediaThumbnailLoader()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Junk Files") },
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
            val junkContentState = when {
                uiState.isScanning && uiState.groups.isEmpty() -> "scanning"
                uiState.groups.isEmpty() -> "empty"
                else -> "list"
            }
            Crossfade(targetState = junkContentState, label = "junkFilesContent") { state ->
                when (state) {
                    "scanning" ->
                        ScanningIndicator(uiState.scannedCount, Modifier.fillMaxSize())
                    "empty" ->
                        EmptyResultsMessage("No junk found. Your storage is clean!", Modifier.fillMaxSize())
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        uiState.groups.forEach { group ->
                            item(key = "header-${group.category}") {
                                val selectedInGroup = group.items.count { it.path in uiState.selectedPaths }
                                val headerState = when (selectedInGroup) {
                                    0 -> ToggleableState.Off
                                    group.items.size -> ToggleableState.On
                                    else -> ToggleableState.Indeterminate
                                }
                                GroupHeader(
                                    title = group.category.label(),
                                    sizeText = FormatUtils.formatFileSize(group.totalBytes),
                                    selectionState = headerState,
                                    onToggleAll = { viewModel.toggleGroup(group.category, headerState != ToggleableState.On) },
                                )
                            }
                            items(group.items, key = { it.path }) { item ->
                                val mediaCategory = FileCategory.fromExtension(File(item.name).extension)
                                val isPreviewable = !item.isDirectory &&
                                    (mediaCategory == FileCategory.IMAGES || mediaCategory == FileCategory.VIDEOS)
                                SelectableFileRow(
                                    title = item.name,
                                    subtitle = item.path,
                                    sizeText = FormatUtils.formatFileSize(item.sizeBytes),
                                    icon = group.category.icon(),
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    checked = item.path in uiState.selectedPaths,
                                    onToggle = { viewModel.toggleSelection(item.path) },
                                    modifier = Modifier.animateItem(),
                                    previewModel = if (isPreviewable) File(item.path) else null,
                                    previewImageLoader = if (isPreviewable) thumbnailLoader else null,
                                )
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
