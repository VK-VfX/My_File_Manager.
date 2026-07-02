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
        )
    }
}

@Composable
private fun DashboardContent(
    uiState: CleanDashboardUiState,
    onNavigateJunk: () -> Unit,
    onNavigateLarge: () -> Unit,
    onNavigateDuplicates: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Clean",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { StorageOverviewCard(uiState) }
        item {
            CleanCategoryCard(
                title = "Junk Files",
                subtitle = teaserSubtitle(uiState.isScanning, uiState.junkTeaser),
                icon = Icons.Filled.DeleteSweep,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = onNavigateJunk,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            CleanCategoryCard(
                title = "Large Files",
                subtitle = teaserSubtitle(uiState.isScanning, uiState.largeTeaser),
                icon = Icons.Filled.PhotoSizeSelectLarge,
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            CleanCategoryCard(
                title = "Duplicate Files",
                subtitle = teaserSubtitle(uiState.isScanning, uiState.duplicateTeaser),
                icon = Icons.Filled.ContentCopy,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onNavigateDuplicates,
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

private fun teaserSubtitle(isScanning: Boolean, teaser: CleanTeaser): String =
    if (isScanning && teaser.itemCount == 0) {
        "Scanning…"
    } else {
        "${teaser.itemCount} items · ${FormatUtils.formatFileSize(teaser.totalBytes)}"
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
            text = "Nimbus Files needs full storage access to scan for junk, large, and duplicate files.",
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
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
