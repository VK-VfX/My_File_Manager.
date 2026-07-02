package com.vfxsal.filemanager.feature.files.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

data class BreadcrumbSegment(val label: String, val path: String)

@Composable
fun BreadcrumbBar(
    path: String,
    rootPath: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = remember(path, rootPath) { buildBreadcrumbSegments(path, rootPath) }
    LazyRow(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(segments, key = { _, segment -> segment.path }) { index, segment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onNavigate(segment.path) }) {
                    Text(segment.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index != segments.lastIndex) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun buildBreadcrumbSegments(path: String, rootPath: String): List<BreadcrumbSegment> {
    val root = File(rootPath)
    val target = File(path)
    val chain = ArrayDeque<File>()
    var current: File? = target
    while (current != null) {
        chain.addFirst(current)
        if (current.absolutePath == root.absolutePath) break
        current = current.parentFile
    }
    if (chain.firstOrNull()?.absolutePath != root.absolutePath) {
        chain.addFirst(root)
    }
    return chain.map { file ->
        val label = if (file.absolutePath == root.absolutePath) "Internal Storage" else file.name
        BreadcrumbSegment(label = label, path = file.absolutePath)
    }
}
