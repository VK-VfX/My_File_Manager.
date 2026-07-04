package com.vfxsal.filemanager.feature.files.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsSheet(
    entry: FileEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTag: () -> Unit,
    onExtract: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(entry.category.color().copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = entry.category.icon, contentDescription = null, tint = entry.category.color())
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (entry.isDirectory) "Folder" else (FileOps.mimeType(entry.file) ?: "Unknown type"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            DetailRow(label = "Location", value = entry.file.parent ?: entry.path)
            if (!entry.isDirectory) {
                DetailRow(label = "Size", value = FormatUtils.formatFileSize(entry.sizeBytes))
            }
            DetailRow(label = "Modified", value = FormatUtils.formatDateTime(entry.lastModified))
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            if (!entry.isDirectory) {
                DetailActionRow(icon = Icons.Filled.OpenInNew, label = "Open", onClick = onOpen)
                DetailActionRow(icon = Icons.Filled.Share, label = "Share", onClick = onShare)
            }
            if (onExtract != null) {
                DetailActionRow(icon = Icons.Filled.FolderZip, label = "Extract", onClick = onExtract)
            }
            DetailActionRow(icon = Icons.Filled.Edit, label = "Rename", onClick = onRename)
            DetailActionRow(icon = Icons.Filled.Label, label = "Tag", onClick = onTag)
            DetailActionRow(icon = Icons.Filled.Delete, label = "Delete", onClick = onDelete)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
