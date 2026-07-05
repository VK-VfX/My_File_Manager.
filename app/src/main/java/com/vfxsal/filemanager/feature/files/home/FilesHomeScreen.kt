package com.vfxsal.filemanager.feature.files.home

import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.components.FileActionsHost
import com.vfxsal.filemanager.feature.files.components.rememberFileActionsState
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.feature.update.UpdateViewModel
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.ui.components.ShimmerHomeContent
import com.vfxsal.filemanager.util.StorageStats
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesHomeScreen(
    onOpenCategory: (FileCategory) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onEditFile: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStorageBreakdown: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenTimeline: () -> Unit,
    viewModel: FilesHomeViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionsState = rememberFileActionsState()
    var updateBannerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) { updateViewModel.checkForUpdate(silent = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatFiles?") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.storageStats == null) {
                ShimmerHomeContent()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item { GreetingHeader() }

                    item { SearchPill(onClick = onOpenSearch) }

                    val availableUpdate = updateState.available
                    if (availableUpdate != null && !updateBannerDismissed) {
                        item {
                            UpdateBannerCard(
                                versionName = availableUpdate.versionName,
                                onClick = onOpenAbout,
                                onDismiss = { updateBannerDismissed = true },
                            )
                        }
                    }

                    item {
                        HomeHeroCarousel(
                            stats = uiState.storageStats,
                            onOpenStorageBreakdown = onOpenStorageBreakdown,
                            onOpenTimeline = onOpenTimeline,
                            onOpenVault = onOpenVault,
                            onOpenTrash = onOpenTrash,
                        )
                    }

                    if (uiState.suggestions.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.suggestions.forEach { suggestion ->
                                    SuggestionCard(suggestion = suggestion, onClick = { onOpenCategory(suggestion.category) })
                                }
                            }
                        }
                    }

                    item {
                        Column {
                            Text("Categories", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            CategoryGrid(
                                summaries = uiState.categorySummaries,
                                downloadsCount = uiState.downloadsCount,
                                downloadsBytes = uiState.downloadsBytes,
                                onCategoryClick = onOpenCategory,
                                onDownloadsClick = {
                                    onOpenDirectory(File(Environment.getExternalStorageDirectory(), "Download").absolutePath)
                                },
                            )
                        }
                    }

                    item {
                        InternalStorageRow(
                            onClick = { onOpenDirectory(Environment.getExternalStorageDirectory().absolutePath) },
                        )
                    }

                    if (uiState.recentFiles.isNotEmpty()) {
                        item {
                            Column {
                                Text("Recent files", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                RecentFilesRow(
                                    files = uiState.recentFiles,
                                    onFileClick = { entry ->
                                        when {
                                            entry.category == FileCategory.IMAGES -> onOpenImage(entry.path)
                                            !FileOps.openOrEdit(context, entry, onEditFile) ->
                                                scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                        }
                                    },
                                    onInfoClick = { entry -> actionsState.showDetails(entry) },
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
}

/**
 * Swipeable hero deck at the top of Files home: Storage, Timeline, Vault and Recycle Bin
 * as one carousel (with the next card peeking in from the edge) instead of four stacked
 * full-width cards, so the category grid is visible without scrolling.
 */
@Composable
private fun HomeHeroCarousel(
    stats: StorageStats?,
    onOpenStorageBreakdown: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val pagerState = rememberPagerState { 4 }
    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(end = 40.dp),
            pageSpacing = 12.dp,
        ) { page ->
            when (page) {
                0 -> StorageHeroCard(stats = stats, onClick = onOpenStorageBreakdown)
                1 -> HeroCard(
                    icon = Icons.Filled.History,
                    title = "Timeline",
                    subtitle = "Browse your photos and videos by date",
                    onClick = onOpenTimeline,
                )
                2 -> HeroCard(
                    icon = Icons.Filled.Lock,
                    title = "Secure Vault",
                    subtitle = "PIN-protected private space for your files",
                    onClick = onOpenVault,
                )
                else -> HeroCard(
                    icon = Icons.Filled.DeleteOutline,
                    title = "Recycle Bin",
                    subtitle = "Restore or permanently delete removed items",
                    onClick = onOpenTrash,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(4) { index ->
                val isCurrent = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isCurrent) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
    }
}

/** Time-of-day greeting that opens the home screen with some personality. */
@Composable
private fun GreetingHeader() {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }
    Column {
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Here's what's on your device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full-width pill that looks like a search field and opens global search on tap. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "Search your files",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Search your files",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Circular used-space gauge: a full track ring with a rounded progress arc over it. */
@Composable
private fun StorageDonut(usedFraction: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * usedFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Text(
            text = "${(usedFraction * 100).toInt()}%",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun StorageHeroCard(stats: StorageStats?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StorageDonut(
                usedFraction = stats?.usedFraction ?: 0f,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                if (stats != null) {
                    Text(
                        text = "${FormatUtils.formatFileSize(stats.usedBytes)} used",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${FormatUtils.formatFileSize(stats.freeBytes)} free of ${FormatUtils.formatFileSize(stats.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "View breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeroCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpdateBannerCard(versionName: String, onClick: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Update available - version $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: HomeSuggestion, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(suggestion.category.color().copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = suggestion.category.color())
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = suggestion.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryGrid(
    summaries: List<CategorySummary>,
    downloadsCount: Int,
    downloadsBytes: Long,
    onCategoryClick: (FileCategory) -> Unit,
    onDownloadsClick: () -> Unit,
) {
    val itemCount = summaries.size + 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var rowStart = 0
        while (rowStart < itemCount) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                for (index in rowStart until minOf(rowStart + 2, itemCount)) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (index < summaries.size) {
                            val summary = summaries[index]
                            CategoryShortcutCard(
                                label = categoryLabel(summary.category),
                                icon = summary.category.icon,
                                color = summary.category.color(),
                                count = summary.count,
                                bytes = summary.totalBytes,
                                onClick = { onCategoryClick(summary.category) },
                            )
                        } else {
                            CategoryShortcutCard(
                                label = "Downloads",
                                icon = Icons.Filled.Download,
                                color = MaterialTheme.colorScheme.tertiary,
                                count = downloadsCount,
                                bytes = downloadsBytes,
                                onClick = onDownloadsClick,
                            )
                        }
                    }
                }
                if (rowStart + 1 >= itemCount) {
                    Spacer(Modifier.weight(1f))
                }
            }
            rowStart += 2
        }
    }
}

@Composable
private fun CategoryShortcutCard(
    label: String,
    icon: ImageVector,
    color: Color,
    count: Int,
    bytes: Long,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (count == 0) "Empty" else "$count · ${FormatUtils.formatFileSize(bytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InternalStorageRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Internal Storage", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Browse all files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentFilesRow(
    files: List<FileEntry>,
    onFileClick: (FileEntry) -> Unit,
    onInfoClick: (FileEntry) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(files, key = { it.path }) { entry ->
            RecentFileCard(entry, onClick = { onFileClick(entry) }, onInfoClick = { onInfoClick(entry) })
        }
    }
}

@Composable
private fun RecentFileCard(entry: FileEntry, onClick: () -> Unit, onInfoClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            val showsThumbnail = entry.category == FileCategory.IMAGES || entry.category == FileCategory.VIDEOS
            if (showsThumbnail) {
                val thumbnailLoader = rememberMediaThumbnailLoader()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(entry.category.color().copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = entry.file,
                        imageLoader = thumbnailLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(entry.category.color().copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(entry.category.icon, contentDescription = null, tint = entry.category.color())
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = FormatUtils.formatFileSize(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onInfoClick, contentPadding = PaddingValues(0.dp)) {
                Text("Details", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

internal fun categoryLabel(category: FileCategory): String = when (category) {
    FileCategory.IMAGES -> "Images"
    FileCategory.VIDEOS -> "Videos"
    FileCategory.AUDIO -> "Audio"
    FileCategory.DOCUMENTS -> "Documents"
    FileCategory.APKS -> "APKs"
    FileCategory.ARCHIVES -> "Archives"
    FileCategory.FOLDER -> "Folders"
    FileCategory.OTHER -> "Other"
}
