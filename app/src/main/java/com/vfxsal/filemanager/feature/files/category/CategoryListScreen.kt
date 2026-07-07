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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.ClipboardViewModel
import com.vfxsal.filemanager.feature.files.browse.SortBy
import com.vfxsal.filemanager.feature.files.components.BatchRenameDialog
import com.vfxsal.filemanager.feature.files.components.DeleteConfirmDialog
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.components.FileActionsHost
import com.vfxsal.filemanager.feature.files.components.FileListItem
import com.vfxsal.filemanager.feature.files.components.SwipeableFileRow
import com.vfxsal.filemanager.feature.files.components.TagPickerDialog
import com.vfxsal.filemanager.feature.files.components.rememberFileActionsState
import com.vfxsal.filemanager.feature.files.home.categoryLabel
import com.vfxsal.filemanager.feature.files.tags.FileTagsStore
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.feature.settings.CategoryViewMode
import com.vfxsal.filemanager.ui.components.ActionBarButton
import com.vfxsal.filemanager.ui.components.LabeledActionBar
import com.vfxsal.filemanager.ui.components.ShimmerFileList
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryListScreen(
    categoryName: String,
    onBack: () -> Unit,
    onEditFile: (String) -> Unit,
    onOpenImage: (path: String, sortBy: SortBy, ascending: Boolean) -> Unit,
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
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

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
                    // Each category screen carries its own accent color so Images feels
                    // green, Videos coral, etc. - the same hues already used on the icons.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = category.color().copy(alpha = 0.12f),
                    ),
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
                        if (category == FileCategory.IMAGES || category == FileCategory.VIDEOS) {
                            val isGrid = uiState.viewMode == CategoryViewMode.GRID
                            IconButton(onClick = {
                                viewModel.setViewMode(if (isGrid) CategoryViewMode.LIST else CategoryViewMode.GRID)
                            }) {
                                Icon(
                                    imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view",
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                CategorySortOptionItem("Name", SortBy.NAME, uiState.sortBy, uiState.ascending) {
                                    viewModel.setSortBy(it)
                                    showSortMenu = false
                                }
                                CategorySortOptionItem("Size", SortBy.SIZE, uiState.sortBy, uiState.ascending) {
                                    viewModel.setSortBy(it)
                                    showSortMenu = false
                                }
                                CategorySortOptionItem("Date modified", SortBy.DATE, uiState.sortBy, uiState.ascending) {
                                    viewModel.setSortBy(it)
                                    showSortMenu = false
                                }
                                CategorySortOptionItem("File type", SortBy.TYPE, uiState.sortBy, uiState.ascending) {
                                    viewModel.setSortBy(it)
                                    showSortMenu = false
                                }
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
                LabeledActionBar {
                    ActionBarButton(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        onClick = {
                            val files = viewModel.selectedEntries().filterNot { it.isDirectory }.map { it.file }
                            if (!FileOps.tryShare(context, files)) {
                                scope.launch { snackbarHostState.showSnackbar("Nothing to share") }
                            }
                        },
                    )
                    ActionBarButton(
                        icon = Icons.Filled.ContentCut,
                        label = "Move",
                        onClick = {
                            clipboardViewModel.cut(viewModel.selectedEntries().map { it.path })
                            viewModel.clearSelection()
                            scope.launch { snackbarHostState.showSnackbar("Ready to move - open the destination folder and paste") }
                        },
                    )
                    ActionBarButton(
                        icon = Icons.Filled.Lock,
                        label = "Vault",
                        onClick = {
                            viewModel.moveSelectedToVault { count ->
                                scope.launch { snackbarHostState.showSnackbar("Moved $count item(s) to the vault") }
                            }
                        },
                    )
                    ActionBarButton(icon = Icons.Filled.Label, label = "Tag", onClick = { showTagDialog = true })
                    ActionBarButton(icon = Icons.Filled.Edit, label = "Rename", onClick = { showBatchRenameDialog = true })
                    ActionBarButton(icon = Icons.Filled.Delete, label = "Delete", onClick = { showDeleteSelectedDialog = true })
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
                    "loading" -> ShimmerFileList()
                    "empty" -> EmptyState(message = "No ${categoryLabel(category).lowercase()} found")
                    else -> {
                        val summary = "${uiState.entries.size} items · ${FormatUtils.formatFileSize(uiState.entries.sumOf { it.sizeBytes })}"
                        val onEntryClick: (FileEntry) -> Unit = { entry ->
                            when {
                                uiState.selectionMode -> viewModel.toggleSelection(entry.path)
                                entry.category == FileCategory.IMAGES ->
                                    onOpenImage(entry.path, uiState.sortBy, uiState.ascending)
                                else -> if (!FileOps.openOrEdit(context, entry, onEditFile)) {
                                    scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                }
                            }
                        }
                        val showGrid = uiState.viewMode == CategoryViewMode.GRID &&
                            (category == FileCategory.IMAGES || category == FileCategory.VIDEOS)
                        if (showGrid) {
                            val thumbnailLoader = rememberMediaThumbnailLoader()
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    CategorySummaryHeader(summary = summary, tint = category.color())
                                }
                                gridItems(uiState.entries, key = { it.path }) { entry ->
                                    MediaGridItem(
                                        entry = entry,
                                        selectionMode = uiState.selectionMode,
                                        selected = uiState.selectedPaths.contains(entry.path),
                                        thumbnailLoader = thumbnailLoader,
                                        onClick = { onEntryClick(entry) },
                                        onLongClick = { viewModel.enterSelectionMode(entry.path) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        } else {
                            Column {
                                CategorySummaryHeader(summary = summary, tint = category.color())
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                                onClick = { onEntryClick(entry) },
                                                onLongClick = { viewModel.enterSelectionMode(entry.path) },
                                                onInfoClick = { actionsState.showDetails(entry) },
                                            )
                                        }
                                    }
                                }
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

@Composable
private fun CategorySortOptionItem(
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

/** Item count + total size, shown as a pill tinted with the category's accent color. */
@Composable
private fun CategorySummaryHeader(summary: String, tint: Color) {
    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(tint.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** One square thumbnail tile in grid view, with a play badge for videos and a
 *  check overlay + accent scrim while multi-selecting. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridItem(
    entry: FileEntry,
    selectionMode: Boolean,
    selected: Boolean,
    thumbnailLoader: ImageLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = entry.file,
            imageLoader = thumbnailLoader,
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
        }
        if (entry.category == FileCategory.VIDEOS) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            )
        }
    }
}
