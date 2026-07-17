package com.vfxsal.filemanager.feature.files.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.ClipboardMode
import com.vfxsal.filemanager.feature.files.ClipboardViewModel
import com.vfxsal.filemanager.feature.files.PasteResult
import com.vfxsal.filemanager.feature.files.components.BatchRenameDialog
import com.vfxsal.filemanager.feature.files.components.BreadcrumbBar
import com.vfxsal.filemanager.feature.files.components.DeleteConfirmDialog
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.components.FileActionsHost
import com.vfxsal.filemanager.feature.files.components.FileListItem
import com.vfxsal.filemanager.feature.files.components.SwipeableFileRow
import com.vfxsal.filemanager.feature.files.components.TagPickerDialog
import com.vfxsal.filemanager.feature.files.components.TextInputDialog
import com.vfxsal.filemanager.feature.files.components.rememberFileActionsState
import com.vfxsal.filemanager.feature.files.components.validateFileName
import com.vfxsal.filemanager.feature.files.tags.FileTagsStore
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.ui.components.ActionBarButton
import com.vfxsal.filemanager.ui.components.LabeledActionBar
import com.vfxsal.filemanager.ui.components.ShimmerFileList
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryBrowserScreen(
    path: String,
    rootPath: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onEditFile: (String) -> Unit,
    onOpenImage: (path: String, sortBy: SortBy, ascending: Boolean) -> Unit,
    clipboardViewModel: ClipboardViewModel,
    viewModel: DirectoryBrowserViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardState by clipboardViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionsState = rememberFileActionsState()

    LaunchedEffect(path) { viewModel.load(path) }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.selectionMode || uiState.searchActive) {
        when {
            uiState.selectionMode -> viewModel.clearSelection()
            uiState.searchActive -> viewModel.setSearchActive(false)
        }
    }

    Scaffold(
        topBar = {
            Column {
                BrowserTopBar(
                    uiState = uiState,
                    currentFolderName = if (path == rootPath) "Internal Storage" else File(path).name,
                    onBack = onBack,
                    onToggleSearch = { viewModel.setSearchActive(!uiState.searchActive) },
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onToggleRecursive = { viewModel.setSearchRecursive(!uiState.searchRecursive) },
                    showSortMenu = showSortMenu,
                    onSortClick = { showSortMenu = true },
                    onSortMenuDismiss = { showSortMenu = false },
                    onSortSelect = { viewModel.setSortBy(it); showSortMenu = false },
                    onClearSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAll() },
                )
                if (!uiState.searchActive) {
                    BreadcrumbBar(
                        path = path,
                        rootPath = rootPath,
                        onNavigate = onNavigate,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                AnimatedVisibility(
                    visible = !clipboardState.isEmpty,
                    enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + expandVertically(tween(200, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    PasteBanner(
                        count = clipboardState.paths.size,
                        mode = clipboardState.mode,
                        onPaste = {
                            clipboardViewModel.pasteInto(path) { result ->
                                scope.launch {
                                    when (result) {
                                        is PasteResult.Success -> {
                                            viewModel.refresh()
                                            snackbarHostState.showSnackbar("Pasted ${result.count} item(s)")
                                        }
                                        is PasteResult.Failure -> snackbarHostState.showSnackbar(result.message)
                                    }
                                }
                            }
                        },
                        onCancel = { clipboardViewModel.clear() },
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.selectionMode,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
                SelectionActionBar(
                    onCopy = {
                        clipboardViewModel.copy(uiState.selectedPaths.toList())
                        viewModel.clearSelection()
                    },
                    onMove = {
                        clipboardViewModel.cut(uiState.selectedPaths.toList())
                        viewModel.clearSelection()
                    },
                    onDelete = { showDeleteSelectedDialog = true },
                    onRename = {
                        if (uiState.selectedPaths.size == 1) {
                            renameTarget = viewModel.selectedEntries().firstOrNull()
                        } else {
                            showBatchRenameDialog = true
                        }
                    },
                    onShare = {
                        val files = viewModel.selectedEntries().filterNot { it.isDirectory }.map { it.file }
                        if (!FileOps.tryShare(context, files)) {
                            scope.launch { snackbarHostState.showSnackbar("Nothing to share") }
                        }
                    },
                    onCompress = {
                        viewModel.compressSelected { success ->
                            scope.launch {
                                snackbarHostState.showSnackbar(if (success) "Compressed" else "Could not compress")
                            }
                        }
                    },
                    onMoveToVault = {
                        viewModel.moveSelectedToVault { count ->
                            scope.launch { snackbarHostState.showSnackbar("Moved $count item(s) to the vault") }
                        }
                    },
                    onTag = { showTagDialog = true },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.selectionMode,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)),
            ) {
                FloatingActionButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val browserContentState = when {
                uiState.isLoading -> "loading"
                uiState.entries.isEmpty() -> "empty"
                else -> "list"
            }
            Crossfade(targetState = browserContentState, label = "directoryBrowserContent") { state ->
                when (state) {
                    "loading" -> ShimmerFileList()
                    "empty" -> EmptyState(
                        message = if (uiState.searchActive && uiState.searchQuery.isNotBlank()) {
                            "No matches"
                        } else {
                            "This folder is empty"
                        },
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.entries, key = { it.path }) { entry ->
                            SwipeableFileRow(
                                enabled = !uiState.selectionMode,
                                canShare = !entry.isDirectory,
                                onShare = {
                                    if (!FileOps.tryShare(context, listOf(entry.file))) {
                                        scope.launch { snackbarHostState.showSnackbar("Unable to share this file") }
                                    }
                                },
                                onDelete = { actionsState.requestDelete(entry) },
                                modifier = Modifier.animateItem(),
                            ) {
                                FileListItem(
                                    entry = entry,
                                    selectionMode = uiState.selectionMode,
                                    selected = uiState.selectedPaths.contains(entry.path),
                                    onClick = {
                                        when {
                                            uiState.selectionMode -> viewModel.toggleSelection(entry.path)
                                            entry.isDirectory -> onNavigate(entry.path)
                                            entry.category == FileCategory.IMAGES ->
                                                onOpenImage(entry.path, uiState.sortBy, uiState.ascending)
                                            else -> if (!FileOps.openOrEdit(context, entry, onEditFile)) {
                                                scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                            }
                                        }
                                    },
                                    onLongClick = { viewModel.enterSelectionMode(entry.path) },
                                    onInfoClick = { actionsState.showDetails(entry) },
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

    if (showNewFolderDialog) {
        TextInputDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            validator = { input -> validateFileName(input, File(path), "") },
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name) { success ->
                    if (!success) scope.launch { snackbarHostState.showSnackbar("Could not create folder") }
                }
            },
        )
    }

    if (showDeleteSelectedDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedPaths.size,
            onDismiss = { showDeleteSelectedDialog = false },
            onConfirm = { permanent ->
                showDeleteSelectedDialog = false
                viewModel.deleteSelected(permanent) { count ->
                    scope.launch { snackbarHostState.showSnackbar("Deleted $count item(s)") }
                }
            },
        )
    }

    renameTarget?.let { entry ->
        TextInputDialog(
            title = "Rename",
            label = "Name",
            initialValue = entry.name,
            confirmLabel = "Rename",
            validator = { input -> validateFileName(input, entry.file.parentFile, entry.name) },
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameEntry(entry, newName) { success ->
                    if (!success) scope.launch { snackbarHostState.showSnackbar("Rename failed") }
                }
            },
        )
    }

    if (showBatchRenameDialog) {
        BatchRenameDialog(
            count = uiState.selectedPaths.size,
            onDismiss = { showBatchRenameDialog = false },
            onConfirm = { baseName, pattern ->
                showBatchRenameDialog = false
                viewModel.renameSelected(baseName, pattern) { count ->
                    scope.launch { snackbarHostState.showSnackbar("Renamed $count item(s)") }
                }
            },
        )
    }

    if (showTagDialog) {
        TagPickerDialog(
            count = uiState.selectedPaths.size,
            currentTag = null,
            onDismiss = { showTagDialog = false },
            onSelect = { tag ->
                FileTagsStore.setTag(context, uiState.selectedPaths, tag)
                showTagDialog = false
                viewModel.clearSelection()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    uiState: DirectoryBrowserUiState,
    currentFolderName: String,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleRecursive: () -> Unit,
    showSortMenu: Boolean,
    onSortClick: () -> Unit,
    onSortMenuDismiss: () -> Unit,
    onSortSelect: (SortBy) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
) {
    when {
        uiState.selectionMode -> TopAppBar(
            title = { Text("${uiState.selectedPaths.size} selected") },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                }
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                }
            },
        )
        uiState.searchActive -> TopAppBar(
            title = {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search this folder") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            },
            navigationIcon = {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            },
            actions = {
                FilterChip(
                    selected = uiState.searchRecursive,
                    onClick = onToggleRecursive,
                    label = { Text("Subfolders") },
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        )
        else -> TopAppBar(
            title = { Text(currentFolderName, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                Box {
                    IconButton(onClick = onSortClick) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = onSortMenuDismiss) {
                        SortOptionItem("Name", SortBy.NAME, uiState.sortBy, uiState.ascending, onSortSelect)
                        SortOptionItem("Size", SortBy.SIZE, uiState.sortBy, uiState.ascending, onSortSelect)
                        SortOptionItem("Date modified", SortBy.DATE, uiState.sortBy, uiState.ascending, onSortSelect)
                        SortOptionItem("File type", SortBy.TYPE, uiState.sortBy, uiState.ascending, onSortSelect)
                    }
                }
            },
        )
    }
}

@Composable
private fun SortOptionItem(
    label: String,
    value: SortBy,
    current: SortBy,
    ascending: Boolean,
    onSelect: (SortBy) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onSelect(value) },
        trailingIcon = {
            if (current == value) {
                Icon(
                    imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun PasteBanner(count: Int, mode: ClipboardMode?, onPaste: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (mode == ClipboardMode.MOVE) Icons.Filled.ContentCut else Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count item(s) ready to paste",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onPaste) { Text("Paste") }
        }
    }
}

@Composable
private fun SelectionActionBar(
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onCompress: () -> Unit,
    onMoveToVault: () -> Unit,
    onTag: () -> Unit,
) {
    LabeledActionBar {
        ActionBarButton(icon = Icons.Filled.ContentCopy, label = "Copy", onClick = onCopy)
        ActionBarButton(icon = Icons.Filled.ContentCut, label = "Move", onClick = onMove)
        ActionBarButton(icon = Icons.Filled.Share, label = "Share", onClick = onShare)
        ActionBarButton(icon = Icons.Filled.FolderZip, label = "Compress", onClick = onCompress)
        ActionBarButton(icon = Icons.Filled.Lock, label = "Vault", onClick = onMoveToVault)
        ActionBarButton(icon = Icons.Filled.Label, label = "Tag", onClick = onTag)
        ActionBarButton(icon = Icons.Filled.Edit, label = "Rename", onClick = onRename)
        ActionBarButton(icon = Icons.Filled.Delete, label = "Delete", onClick = onDelete)
    }
}
