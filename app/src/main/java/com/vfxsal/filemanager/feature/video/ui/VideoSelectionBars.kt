package com.vfxsal.filemanager.feature.video.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.vfxsal.filemanager.ui.components.ActionBarButton
import com.vfxsal.filemanager.ui.components.LabeledActionBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSelectionTopBar(selectedCount: Int, onClear: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
            }
        },
    )
}

@Composable
fun VideoSelectionBottomBar(onShare: () -> Unit, onDelete: () -> Unit) {
    LabeledActionBar {
        ActionBarButton(icon = Icons.Filled.Share, label = "Share", onClick = onShare)
        ActionBarButton(icon = Icons.Filled.Delete, label = "Delete", onClick = onDelete)
    }
}

/** Shares one or more videos via their MediaStore content URIs - no FileProvider needed. */
fun shareVideos(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "video/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
