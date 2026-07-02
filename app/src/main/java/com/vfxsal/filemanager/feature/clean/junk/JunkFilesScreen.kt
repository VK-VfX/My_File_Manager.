package com.vfxsal.filemanager.feature.clean.junk

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.feature.clean.ui.CleanBottomBar
import com.vfxsal.filemanager.feature.clean.ui.DeleteConfirmationDialog
import com.vfxsal.filemanager.feature.clean.ui.EmptyResultsMessage
import com.vfxsal.filemanager.feature.clean.ui.GroupHeader
import com.vfxsal.filemanager.feature.clean.ui.ScanningIndicator
import com.vfxsal.filemanager.feature.clean.ui.SelectableFileRow
import com.vfxsal.filemanager.feature.clean.ui.icon
import com.vfxsal.filemanager.feature.clean.ui.label
import com.vfxsal.filemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunkFilesScreen(
    onBack: () -> Unit,
    viewModel: JunkFilesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

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
            if (uiState.groups.isNotEmpty()) {
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
            when {
                uiState.isScanning && uiState.groups.isEmpty() ->
                    ScanningIndicator(uiState.scannedCount, Modifier.fillMaxSize())
                uiState.groups.isEmpty() ->
                    EmptyResultsMessage("No junk found. Your storage is clean!", Modifier.fillMaxSize())
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    uiState.groups.forEach { group ->
                        item(key = "header-${group.category}") {
                            val selectedInGroup = group.items.count { it.path in uiState.selectedPaths }
                            val state = when (selectedInGroup) {
                                0 -> ToggleableState.Off
                                group.items.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            GroupHeader(
                                title = group.category.label(),
                                sizeText = FormatUtils.formatFileSize(group.totalBytes),
                                selectionState = state,
                                onToggleAll = { viewModel.toggleGroup(group.category, state != ToggleableState.On) },
                            )
                        }
                        items(group.items, key = { it.path }) { item ->
                            SelectableFileRow(
                                title = item.name,
                                subtitle = item.path,
                                sizeText = FormatUtils.formatFileSize(item.sizeBytes),
                                icon = group.category.icon(),
                                iconTint = MaterialTheme.colorScheme.primary,
                                checked = item.path in uiState.selectedPaths,
                                onToggle = { viewModel.toggleSelection(item.path) },
                            )
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
