package com.vfxsal.filemanager.feature.files.home

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator
import com.vfxsal.filemanager.util.StorageStats
import com.vfxsal.filemanager.util.rememberMediaThumbnailLoader
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesHomeScreen(
    onOpenCategory: (FileCategory) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onEditFile: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStorageBreakdown: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenTimeline: () -> Unit,
    viewModel: FilesHomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionsState = rememberFileActionsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search all files")
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
                CurlyLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item { StorageUsageCard(uiState.storageStats, onClick = onOpenStorageBreakdown) }

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

                    item {
                        TimelineRow(onClick = onOpenTimeline)
                    }

                    item {
                        VaultRow(onClick = onOpenVault)
                    }

                    item {
                        RecycleBinRow(onClick = onOpenTrash)
                    }

                    if (uiState.recentFiles.isNotEmpty()) {
                        item {
                            Column {
                                Text("Recent files", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                RecentFilesRow(
                                    files = uiState.recentFiles,
                                    onFileClick = { entry ->
                                        if (!FileOps.openOrEdit(context, entry, onEditFile)) {
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

@Composable
private fun StorageUsageCard(stats: StorageStats?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Storage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (stats != null) {
                LinearProgressIndicator(
                    progress = { stats.usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${FormatUtils.formatFileSize(stats.usedBytes)} of ${FormatUtils.formatFileSize(stats.totalBytes)} used",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun TimelineRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Timeline", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Browse your photos and videos by date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VaultRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Secure Vault", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "PIN-protected private space for your files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecycleBinRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Recycle Bin", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Restore or permanently delete recently removed items",
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
