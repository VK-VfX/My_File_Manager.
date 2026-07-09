package com.vfxsal.filemanager.feature.tools.viewer

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ViewerKind { TEXT, IMAGE, VIDEO, AUDIO, OTHER }

private const val MAX_TEXT_BYTES = 2 * 1024 * 1024 // don't try to render huge files inline

/**
 * A lightweight, self-contained previewer: pick any file and it reads text, shows images, or
 * plays audio/video inline without leaving the app. Anything it can't render hands off to an
 * external app. Uses the Storage Access Framework picker, so it needs no storage permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickViewerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var displayName by remember { mutableStateOf("") }
    var mimeType by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uri = picked
            displayName = queryDisplayName(context, picked)
            mimeType = context.contentResolver.getType(picked) ?: guessMime(picked)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (displayName.isBlank()) "Quick Viewer" else displayName, maxLines = 1) },
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
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            val currentUri = uri
            if (currentUri == null) {
                PickPrompt(onPick = { picker.launch(arrayOf("*/*")) })
            } else {
                when (kindOf(mimeType)) {
                    ViewerKind.TEXT -> TextPreview(context = context, uri = currentUri)
                    ViewerKind.IMAGE -> AsyncImage(
                        model = currentUri,
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                    ViewerKind.VIDEO, ViewerKind.AUDIO -> MediaPreview(uri = currentUri)
                    ViewerKind.OTHER -> UnsupportedPreview(
                        name = displayName,
                        onOpenExternally = { openExternally(context, currentUri, mimeType) },
                        onPickAnother = { picker.launch(arrayOf("*/*")) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickPrompt(onPick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.FileOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Open a file to preview it here.\nText, images, audio and video are supported.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPick) { Text("Choose file") }
    }
}

@Composable
private fun TextPreview(context: android.content.Context, uri: Uri) {
    var text by remember(uri) { mutableStateOf<String?>(null) }
    var truncated by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val (content, wasTruncated) = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // Cap the read so a multi-GB file can't OOM the preview. readNBytes is API 33+,
                    // so read manually to stay compatible down to minSdk.
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    var cut = false
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        val remaining = MAX_TEXT_BYTES - total
                        if (read >= remaining) {
                            out.write(buffer, 0, remaining)
                            cut = true
                            break
                        }
                        out.write(buffer, 0, read)
                        total += read
                    }
                    out.toByteArray().decodeToString() to cut
                } ?: ("" to false)
            }.getOrDefault("Could not read file." to false)
        }
        text = content
        truncated = wasTruncated
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = text ?: "Loading…",
            style = MaterialTheme.typography.bodySmall,
        )
        if (truncated) {
            Spacer(Modifier.height(12.dp))
            Text(
                "… file truncated for preview.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaPreview(uri: Uri) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { it.player = player },
    )
}

@Composable
private fun UnsupportedPreview(name: String, onOpenExternally: () -> Unit, onPickAnother: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            "Can't preview \"$name\" here.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenExternally) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Open with another app")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPickAnother) { Text("Choose another file") }
    }
}

private fun kindOf(mime: String?): ViewerKind = when {
    mime == null -> ViewerKind.OTHER
    mime.startsWith("text/") -> ViewerKind.TEXT
    mime == "application/json" || mime == "application/xml" -> ViewerKind.TEXT
    mime.startsWith("image/") -> ViewerKind.IMAGE
    mime.startsWith("video/") -> ViewerKind.VIDEO
    mime.startsWith("audio/") -> ViewerKind.AUDIO
    else -> ViewerKind.OTHER
}

private fun guessMime(uri: Uri): String? {
    val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}

private fun openExternally(context: android.content.Context, uri: Uri, mime: String?) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
