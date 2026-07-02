package com.vfxsal.filemanager.feature.clean.large

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.feature.clean.model.LARGE_FILE_THRESHOLDS
import com.vfxsal.filemanager.feature.clean.ui.CleanBottomBar
import com.vfxsal.filemanager.feature.clean.ui.DeleteConfirmationDialog
import com.vfxsal.filemanager.feature.clean.ui.EmptyResultsMessage
import com.vfxsal.filemanager.feature.clean.ui.GroupHeader
import com.vfxsal.filemanager.feature.clean.ui.ScanningIndicator
import com.vfxsal.filemanager.feature.clean.ui.SelectableFileRow
import com.vfxsal.filemanager.feature.clean.ui.label
import com.vfxsal.filemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFilesScreen(
    onBack: () -> Unit,
    viewModel: LargeFilesViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    var thresholdMenuExpanded by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val thresholdLabel = LARGE_FILE_THRESHOLDS.firstOrNull { it.bytes == uiState.thresholdBytes }?.label
        ?: FormatUtils.formatFileSize(uiState.thresholdBytes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Large Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { thresholdMenuExpanded = true }) {
                            Text("> $thresholdLabel")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = thresholdMenuExpanded, onDismissRequest = { thresholdMenuExpanded = false }) {
                            LARGE_FILE_THRESHOLDS.forEach { threshold ->
                                DropdownMenuItem(
                                    text = { Text(threshold.label) },
                                    onClick = {
                                        thresholdMenuExpanded = false
                                        viewModel.setThreshold(threshold.bytes)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::scan, enabled = !uiState.isScanning) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.files.isNotEmpty()) {
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
            isRefreshing = uiState.isScanning && uiState.files.isNotEmpty(),
            onRefresh = viewModel::scan,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.isScanning && uiState.files.isEmpty() ->
                    ScanningIndicator(uiState.scannedCount, Modifier.fillMaxSize())
                uiState.files.isEmpty() ->
                    EmptyResultsMessage("No files larger than $thresholdLabel found.", Modifier.fillMaxSize())
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    uiState.groupedByCategory.forEach { (category, entries) ->
                        item(key = "header-$category") {
                            val selectedInGroup = entries.count { it.path in uiState.selectedPaths }
                            val state = when (selectedInGroup) {
                                0 -> ToggleableState.Off
                                entries.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            GroupHeader(
                                title = category.label(),
                                sizeText = FormatUtils.formatFileSize(entries.sumOf { it.sizeBytes }),
                                selectionState = state,
                                onToggleAll = { viewModel.toggleCategory(category, state != ToggleableState.On) },
                            )
                        }
                        items(entries, key = { it.path }) { entry ->
                            SelectableFileRow(
                                title = entry.name,
                                subtitle = FormatUtils.formatDate(entry.lastModified),
                                sizeText = FormatUtils.formatFileSize(entry.sizeBytes),
                                icon = entry.category.icon,
                                iconTint = entry.category.color(),
                                checked = entry.path in uiState.selectedPaths,
                                onToggle = { viewModel.toggleSelection(entry.path) },
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
