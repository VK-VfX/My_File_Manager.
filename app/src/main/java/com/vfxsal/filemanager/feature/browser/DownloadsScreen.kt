package com.vfxsal.filemanager.feature.browser

import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val _downloads = MutableStateFlow<List<DownloadOps.DownloadInfo>>(emptyList())
    val downloads: StateFlow<List<DownloadOps.DownloadInfo>> = _downloads.asStateFlow()

    init {
        // Poll while the screen is open - DownloadManager has no push API for progress.
        viewModelScope.launch {
            while (isActive) {
                _downloads.value = withContext(Dispatchers.IO) { DownloadOps.list(getApplication()) }
                delay(700)
            }
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DownloadOps.remove(getApplication(), id) }
            _downloads.value = withContext(Dispatchers.IO) { DownloadOps.list(getApplication()) }
        }
    }

    fun retry(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { DownloadOps.retry(getApplication(), id) }
            _downloads.value = withContext(Dispatchers.IO) { DownloadOps.list(getApplication()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = viewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val hlsJobs by HlsDownloadManager.jobs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (downloads.isEmpty() && hlsJobs.isEmpty()) {
                EmptyState(
                    message = "No downloads yet.\nFiles you download in the browser will appear here.",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(hlsJobs, key = { "hls_" + it.id }) { job ->
                        HlsJobRow(
                            job = job,
                            onOpen = {
                                val file = (job.status as? HlsJobStatus.Done)?.file ?: return@HlsJobRow
                                runCatching { context.startActivity(FileOps.viewIntent(context, file)) }
                                    .onFailure {
                                        scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                    }
                            },
                            onCancel = { HlsDownloadManager.cancel(job.id) },
                            onDismiss = { HlsDownloadManager.dismiss(job.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    items(downloads, key = { it.id }) { download ->
                        DownloadRow(
                            download = download,
                            onOpen = {
                                val uri = DownloadOps.openDownloadedFileUri(context, download.id)
                                if (uri != null) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, download.mediaType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(intent) }.onFailure {
                                        scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                    }
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("File not available") }
                                }
                            },
                            onRemove = { viewModel.remove(download.id) },
                            onRetry = { viewModel.retry(download.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HlsJobRow(
    job: HlsJob,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (job.status is HlsJobStatus.Done) Modifier.clickable(onClick = onOpen) else Modifier),
        ) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (val status = job.status) {
                is HlsJobStatus.Downloading -> {
                    Spacer(Modifier.height(6.dp))
                    if (status.total > 0) {
                        LinearProgressIndicator(
                            progress = { status.completed / status.total.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${status.completed} / ${status.total} segments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Fetching playlist…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is HlsJobStatus.Done -> Text(
                    text = "Done · ${status.file.name} · tap to open",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is HlsJobStatus.Failed -> Text(
                    text = "Failed · ${status.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        IconButton(onClick = if (job.status is HlsJobStatus.Downloading) onCancel else onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = if (job.status is HlsJobStatus.Downloading) "Cancel download" else "Remove from list",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadOps.DownloadInfo,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mediaType = download.mediaType.orEmpty()
        val icon = when {
            mediaType.startsWith("image/") -> Icons.Filled.Image
            mediaType.startsWith("video/") -> Icons.Filled.Movie
            mediaType.startsWith("audio/") -> Icons.Filled.Audiotrack
            else -> Icons.Filled.InsertDriveFile
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (download.isSuccessful) Modifier.clickable(onClick = onOpen) else Modifier),
        ) {
            Text(
                text = download.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                download.isRunning -> {
                    Spacer(Modifier.height(6.dp))
                    if (download.bytesTotal > 0) {
                        LinearProgressIndicator(
                            progress = { download.progressFraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${FormatUtils.formatFileSize(download.bytesDownloaded)} of ${FormatUtils.formatFileSize(download.bytesTotal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                    }
                }
                download.isSuccessful -> Text(
                    text = "Done · ${FormatUtils.formatFileSize(download.bytesTotal)} · tap to open",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    text = if (download.status == DownloadManager.STATUS_FAILED) "Failed" else "Waiting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (download.isFailed) {
            IconButton(onClick = onRetry) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Retry download",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = if (download.isRunning) "Cancel download" else "Remove from list",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
