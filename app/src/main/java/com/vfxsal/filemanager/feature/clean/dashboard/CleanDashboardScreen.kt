package com.vfxsal.filemanager.feature.clean.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.clean.ui.label
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanDashboardScreen(
    onNavigateJunk: () -> Unit,
    onNavigateLarge: () -> Unit,
    onNavigateDuplicates: () -> Unit,
    onNavigateSimilarPhotos: () -> Unit,
    viewModel: CleanDashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(PermissionUtils.hasAllFilesAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowHasAccess = PermissionUtils.hasAllFilesAccess(context)
                if (nowHasAccess && !hasAccess) viewModel.refresh()
                hasAccess = nowHasAccess
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasAccess) {
        StoragePermissionRequired(
            onGrantClick = { context.startActivity(PermissionUtils.allFilesAccessIntent(context)) },
        )
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isScanning,
        onRefresh = viewModel::refresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
        DashboardContent(
            uiState = uiState,
            onNavigateJunk = onNavigateJunk,
            onNavigateLarge = onNavigateLarge,
            onNavigateDuplicates = onNavigateDuplicates,
            onNavigateSimilarPhotos = onNavigateSimilarPhotos,
        )
    }
}

@Composable
private fun DashboardContent(
    uiState: CleanDashboardUiState,
    onNavigateJunk: () -> Unit,
    onNavigateLarge: () -> Unit,
    onNavigateDuplicates: () -> Unit,
    onNavigateSimilarPhotos: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CleanHealthHeader(uiState) }
        item { StorageOverviewCard(uiState) }
        item { ReclaimableCard(uiState, modifier = Modifier.padding(horizontal = 16.dp)) }
        item {
            Text(
                text = "Clean-up tools",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
        item {
            CleanCategoryCard(
                title = "Junk Files",
                description = "App caches, empty folders and leftover installers",
                icon = Icons.Filled.DeleteSweep,
                iconTint = MaterialTheme.colorScheme.error,
                teaser = uiState.junkTeaser,
                isScanning = uiState.isScanning,
                onClick = onNavigateJunk,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            CleanCategoryCard(
                title = "Large Files",
                description = "The biggest files taking up space on this device",
                icon = Icons.Filled.PhotoSizeSelectLarge,
                iconTint = MaterialTheme.colorScheme.tertiary,
                teaser = uiState.largeTeaser,
                isScanning = uiState.isScanning,
                onClick = onNavigateLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            CleanCategoryCard(
                title = "Duplicate Files",
                description = "Exact copies of the same file stored more than once",
                icon = Icons.Filled.ContentCopy,
                iconTint = MaterialTheme.colorScheme.primary,
                teaser = uiState.duplicateTeaser,
                isScanning = uiState.isScanning,
                onClick = onNavigateDuplicates,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            CleanCategoryCard(
                title = "Similar Photos",
                description = "Near-identical shots you probably don't need twice",
                icon = Icons.Filled.Collections,
                iconTint = MaterialTheme.colorScheme.secondary,
                teaser = null,
                isScanning = uiState.isScanning,
                onClick = onNavigateSimilarPhotos,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        uiState.error?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

/** Big title plus a one-line storage health verdict, colored by how full the device is. */
@Composable
private fun CleanHealthHeader(uiState: CleanDashboardUiState) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Clean", style = MaterialTheme.typography.headlineMedium)
        val stats = uiState.storageStats
        if (stats != null) {
            val freeFraction = 1f - stats.usedFraction
            val healthText: String
            val healthColor: Color
            when {
                freeFraction > 0.25f -> {
                    healthText = "Storage is healthy"
                    healthColor = MaterialTheme.colorScheme.tertiary
                }
                freeFraction > 0.10f -> {
                    healthText = "Storage is getting full"
                    healthColor = MaterialTheme.colorScheme.primary
                }
                else -> {
                    healthText = "Storage is almost full"
                    healthColor = MaterialTheme.colorScheme.error
                }
            }
            Text(
                text = "$healthText · ${FormatUtils.formatFileSize(stats.freeBytes)} free",
                style = MaterialTheme.typography.bodyMedium,
                color = healthColor,
            )
        }
    }
}

/** Highlights the total space the junk and duplicate scanners believe can be freed. */
@Composable
private fun ReclaimableCard(uiState: CleanDashboardUiState, modifier: Modifier = Modifier) {
    val reclaimable = uiState.junkTeaser.totalBytes + uiState.duplicateTeaser.totalBytes
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (uiState.isScanning && reclaimable == 0L) {
                        "Scanning for savings…"
                    } else {
                        "Up to ${FormatUtils.formatFileSize(reclaimable)} can be freed"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "From junk files and duplicates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun StoragePermissionRequired(onGrantClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Storage access needed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "WhatFiles? needs full storage access to scan for junk, large, and duplicate files.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrantClick) { Text("Grant access") }
    }
}

@Composable
private fun StorageOverviewCard(uiState: CleanDashboardUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CategoryDonutChart(categoryTotals = uiState.categoryTotals, modifier = Modifier.size(140.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val stats = uiState.storageStats
                        Text(
                            text = stats?.let { FormatUtils.formatFileSize(it.usedBytes) } ?: "--",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    val stats = uiState.storageStats
                    if (stats != null) {
                        LabeledStat("Free", FormatUtils.formatFileSize(stats.freeBytes))
                        Spacer(Modifier.height(6.dp))
                        LabeledStat("Total", FormatUtils.formatFileSize(stats.totalBytes))
                        LinearProgressIndicator(
                            progress = { stats.usedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
            if (uiState.categoryTotals.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                CategoryLegend(uiState.categoryTotals)
            }
            if (uiState.isScanning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LabeledStat(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CategoryDonutChart(categoryTotals: Map<FileCategory, Long>, modifier: Modifier = Modifier) {
    val categoryColors = FileCategory.entries.associateWith { it.color() }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = categoryTotals.values.sum().coerceAtLeast(1L)
    val entries = categoryTotals.entries.filter { it.value > 0 }.sortedByDescending { it.value }

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

        if (entries.isEmpty()) {
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            return@Canvas
        }

        var startAngle = -90f
        entries.forEach { (category, bytes) ->
            val sweep = (360f * bytes.toFloat() / total.toFloat()).coerceAtLeast(2f)
            drawArc(
                color = categoryColors[category] ?: trackColor,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun CategoryLegend(categoryTotals: Map<FileCategory, Long>) {
    val total = categoryTotals.values.sum().coerceAtLeast(1L)
    val entries = categoryTotals.entries.filter { it.value > 0 }.sortedByDescending { it.value }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { (category, bytes) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(category.color()),
                )
                Spacer(Modifier.width(8.dp))
                Text(category.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = FormatUtils.formatFileSize(bytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(bytes * 100 / total)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun CleanCategoryCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    teaser: CleanTeaser?,
    isScanning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    teaser == null -> StatPill(text = "Tap to scan", tint = iconTint)
                    isScanning && teaser.itemCount == 0 -> StatPill(text = "Scanning…", tint = iconTint)
                    else -> {
                        StatPill(text = "${teaser.itemCount} items", tint = iconTint)
                        StatPill(text = FormatUtils.formatFileSize(teaser.totalBytes), tint = iconTint)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String, tint: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
