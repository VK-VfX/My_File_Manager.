package com.vfxsal.filemanager.feature.tools.dualpane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.util.ArchiveOps
import java.io.File
import kotlin.math.roundToInt

/** Transient state describing the file currently being dragged between panes. */
private data class DragState(
    val from: PaneSide,
    val file: File,
    val label: String,
    val rootPos: Offset,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualPaneScreen(
    onBack: () -> Unit,
    viewModel: DualPaneViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var leftBounds by remember { mutableStateOf(Rect.Zero) }
    var rightBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<DragState?>(null) }

    fun paneAt(pos: Offset): PaneSide? = when {
        leftBounds.contains(pos) -> PaneSide.LEFT
        rightBounds.contains(pos) -> PaneSide.RIGHT
        else -> null
    }

    val hoverSide = drag?.let { paneAt(it.rootPos) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual Pane") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { boxOrigin = it.boundsInRoot().topLeft },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxSize()) {
                    PaneColumn(
                        side = PaneSide.LEFT,
                        pane = state.left,
                        canGoUp = viewModel.canGoUp(PaneSide.LEFT),
                        isDropTarget = drag != null && drag?.from != PaneSide.LEFT && hoverSide == PaneSide.LEFT,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { leftBounds = it.boundsInRoot() },
                        viewModel = viewModel,
                        onDragStart = { file, label, pos -> drag = DragState(PaneSide.LEFT, file, label, pos) },
                        onDrag = { pos -> drag = drag?.copy(rootPos = pos) },
                        onDragEnd = {
                            drag?.let { d ->
                                if (paneAt(d.rootPos) == PaneSide.RIGHT) viewModel.dropCopy(d.from, d.file)
                            }
                            drag = null
                        },
                    )
                    VerticalDivider()
                    PaneColumn(
                        side = PaneSide.RIGHT,
                        pane = state.right,
                        canGoUp = viewModel.canGoUp(PaneSide.RIGHT),
                        isDropTarget = drag != null && drag?.from != PaneSide.RIGHT && hoverSide == PaneSide.RIGHT,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { rightBounds = it.boundsInRoot() },
                        viewModel = viewModel,
                        onDragStart = { file, label, pos -> drag = DragState(PaneSide.RIGHT, file, label, pos) },
                        onDrag = { pos -> drag = drag?.copy(rootPos = pos) },
                        onDragEnd = {
                            drag?.let { d ->
                                if (paneAt(d.rootPos) == PaneSide.LEFT) viewModel.dropCopy(d.from, d.file)
                            }
                            drag = null
                        },
                    )
                }
            }

            // Floating "ghost" chip that follows the finger while dragging.
            drag?.let { d ->
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.offset {
                        IntOffset(
                            (d.rootPos.x - boxOrigin.x + 16f).roundToInt(),
                            (d.rootPos.y - boxOrigin.y - 24f).roundToInt(),
                        )
                    },
                ) {
                    Text(
                        text = d.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthMax()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.widthMax(): Modifier = this.width(200.dp)

@Composable
private fun PaneColumn(
    side: PaneSide,
    pane: PaneUiState,
    canGoUp: Boolean,
    isDropTarget: Boolean,
    viewModel: DualPaneViewModel,
    onDragStart: (File, String, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isDropTarget) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = modifier
            .background(if (isDropTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent)
            .border(2.dp, borderColor),
    ) {
        // Path header + up button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.goUp(side) }, enabled = canGoUp) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Up one folder")
            }
            Text(
                text = pane.dir.name.ifBlank { "/" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()

        Box(Modifier.weight(1f)) {
            if (pane.entries.isEmpty() && !pane.isLoading) {
                EmptyState(message = "Empty folder")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(pane.entries, key = { it.path }) { entry ->
                        PaneRow(
                            entry = entry,
                            selected = entry.path in pane.selected,
                            onTap = {
                                if (entry.isDirectory) viewModel.open(side, entry.path)
                                else viewModel.toggleSelect(side, entry.path)
                            },
                            onDragStart = { pos -> onDragStart(entry.file, entry.name, pos) },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                        )
                    }
                }
            }
        }

        if (pane.selected.isNotEmpty()) {
            HorizontalDivider()
            PaneActionBar(
                side = side,
                selectedCount = pane.selected.size,
                singleArchive = singleExtractable(pane),
                viewModel = viewModel,
            )
        }
    }
}

private fun singleExtractable(pane: PaneUiState): File? {
    if (pane.selected.size != 1) return null
    val file = File(pane.selected.first())
    return if (ArchiveOps.isExtractable(file)) file else null
}

@Composable
private fun PaneRow(
    entry: FileEntry,
    selected: Boolean,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var rowTopLeft by remember { mutableStateOf(Offset.Zero) }
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowTopLeft = it.boundsInRoot().topLeft }
            .background(bg)
            .clickable(onClick = onTap)
            .pointerInput(entry.path) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local -> onDragStart(rowTopLeft + local) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(rowTopLeft + change.position)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PaneActionBar(
    side: PaneSide,
    selectedCount: Int,
    singleArchive: File?,
    viewModel: DualPaneViewModel,
) {
    var showCompressMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { viewModel.transferSelection(side, move = false) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $selectedCount to other pane")
        }
        IconButton(onClick = { viewModel.transferSelection(side, move = true) }) {
            Icon(Icons.Filled.ContentCut, contentDescription = "Move $selectedCount to other pane")
        }
        Box {
            IconButton(onClick = { showCompressMenu = true }) {
                Icon(Icons.Filled.FolderZip, contentDescription = "Compress selection")
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showCompressMenu,
                onDismissRequest = { showCompressMenu = false },
            ) {
                ArchiveOps.Format.entries.forEach { format ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Compress to ${format.label}") },
                        onClick = {
                            showCompressMenu = false
                            viewModel.compressSelection(side, format)
                        },
                    )
                }
            }
        }
        if (singleArchive != null) {
            IconButton(onClick = { viewModel.extract(side, singleArchive) }) {
                Icon(Icons.Filled.Unarchive, contentDescription = "Extract archive")
            }
        }
        IconButton(onClick = { viewModel.deleteSelection(side) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete $selectedCount")
        }
    }
}
