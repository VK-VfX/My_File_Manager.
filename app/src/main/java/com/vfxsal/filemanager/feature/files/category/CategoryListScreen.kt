package com.vfxsal.filemanager.feature.files.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.files.ClipboardViewModel
import com.vfxsal.filemanager.feature.files.components.DeleteConfirmDialog
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.components.FileActionsHost
import com.vfxsal.filemanager.feature.files.components.FileListItem
import com.vfxsal.filemanager.feature.files.components.rememberFileActionsState
import com.vfxsal.filemanager.feature.files.home.categoryLabel
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator
import com.vfxsal.filemanager.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    categoryName: String,
    onBack: () -> Unit,
    onEditFile: (String) -> Unit,
    clipboardViewModel: ClipboardViewModel,
    onOpenInstalledApps: () -> Unit = {},
    viewModel: CategoryViewModel = viewModel(),
) {
    val category = remember(categoryName) {
        runCatching { FileCategory.valueOf(categoryName) }.getOrDefault(FileCategory.OTHER)
    }
    LaunchedEffect(category) { viewModel.load(category) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionsState = rememberFileActionsState()
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.selectionMode) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (uiState.selectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedPaths.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(categoryLabel(category)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (category == FileCategory.APKS) {
                            IconButton(onClick = onOpenInstalledApps) {
                                Icon(Icons.Filled.Apps, contentDescription = "Installed apps")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.selectionMode,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
                BottomAppBar {
                    IconButton(onClick = {
                        val files = viewModel.selectedEntries().filterNot { it.isDirectory }.map { it.file }
                        if (!FileOps.tryShare(context, files)) {
                            scope.launch { snackbarHostState.showSnackbar("Nothing to share") }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        clipboardViewModel.cut(viewModel.selectedEntries().map { it.path })
                        viewModel.clearSelection()
                        scope.launch { snackbarHostState.showSnackbar("Ready to move - open the destination folder and paste") }
                    }) {
                        Icon(Icons.Filled.ContentCut, contentDescription = "Move")
                    }
                    IconButton(onClick = {
                        viewModel.moveSelectedToVault { count ->
                            scope.launch { snackbarHostState.showSnackbar("Moved $count item(s) to the vault") }
                        }
                    }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Move to vault")
                    }
                    IconButton(onClick = { showDeleteSelectedDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val categoryContentState = when {
                uiState.isLoading -> "loading"
                uiState.entries.isEmpty() -> "empty"
                else -> "list"
            }
            Crossfade(targetState = categoryContentState, label = "categoryListContent") { state ->
                when (state) {
                    "loading" -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CurlyLoadingIndicator()
                    }
                    "empty" -> EmptyState(message = "No ${categoryLabel(category).lowercase()} found")
                    else -> Column {
                        Text(
                            text = "${uiState.entries.size} items · ${FormatUtils.formatFileSize(uiState.entries.sumOf { it.sizeBytes })}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.entries, key = { it.path }) { entry ->
                                FileListItem(
                                    entry = entry,
                                    selectionMode = uiState.selectionMode,
                                    selected = uiState.selectedPaths.contains(entry.path),
                                    onClick = {
                                        when {
                                            uiState.selectionMode -> viewModel.toggleSelection(entry.path)
                                            else -> if (!FileOps.openOrEdit(context, entry, onEditFile)) {
                                                scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                            }
                                        }
                                    },
                                    onLongClick = { viewModel.enterSelectionMode(entry.path) },
                                    onInfoClick = { actionsState.showDetails(entry) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            FileActionsHost(
                state = actionsState,
                snackbarHostState = snackbarHostState,
                onChanged = { viewModel.refresh() },
                onEditFile = onEditFile,
            )
        }
    }

    if (showDeleteSelectedDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedPaths.size,
            onDismiss = { showDeleteSelectedDialog = false },
            onConfirm = {
                showDeleteSelectedDialog = false
                viewModel.deleteSelected { count ->
                    scope.launch { snackbarHostState.showSnackbar("Deleted $count item(s)") }
                }
            },
        )
    }
}
