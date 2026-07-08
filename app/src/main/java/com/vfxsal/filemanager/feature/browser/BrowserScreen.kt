package com.vfxsal.filemanager.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONTokener

private const val HOME_URL = "https://duckduckgo.com"

/** Turns address-bar input into a URL: full URLs pass through, bare domains get https://,
 *  and anything that doesn't look like a host becomes a web search. */
internal fun normalizeInput(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> "https://duckduckgo.com/?q=" + URLEncoder.encode(trimmed, "UTF-8")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var addressText by remember { mutableStateOf(HOME_URL) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var pendingImageDownload by remember { mutableStateOf<String?>(null) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Media spotted on the current page (network sniffing + DOM scan), newest page only.
    val detectedMedia = remember { mutableStateListOf<DetectedMedia>() }
    val addDetected: (String?) -> Unit = { url ->
        val kind = MediaSniffer.classify(url)
        if (kind != null && url != null && detectedMedia.none { it.url == url }) {
            detectedMedia.add(DetectedMedia(url, kind))
        }
    }
    val startDownload: (String) -> Unit = { url ->
        val id = DownloadOps.enqueue(
            context = context,
            url = url,
            userAgent = webViewRef?.settings?.userAgentString,
            mimeType = MediaSniffer.mimeTypeFor(url),
        )
        scope.launch {
            snackbarHostState.showSnackbar(
                if (id != null) "Download started - see Downloads" else "Could not start download",
            )
        }
    }

    // Hardware/gesture back navigates page history first, then leaves the browser.
    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search or enter address", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                webViewRef?.loadUrl(normalizeInput(addressText))
                            }),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.Close, contentDescription = "Close browser")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Page back")
                        }
                        IconButton(onClick = { webViewRef?.goForward() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Page forward")
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload page")
                        }
                        IconButton(onClick = { showMediaSheet = true }, enabled = detectedMedia.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    if (detectedMedia.isNotEmpty()) {
                                        Badge { Text("${detectedMedia.size}") }
                                    }
                                },
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = "Media found on this page")
                            }
                        }
                        IconButton(onClick = onOpenDownloads) {
                            Icon(Icons.Filled.Download, contentDescription = "Downloads")
                        }
                    },
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { pageProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mediaPlaybackRequiresUserGesture = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let { addressText = it }
                                canGoBack = view?.canGoBack() == true
                                detectedMedia.clear()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                // Scan the DOM for video/audio elements whose media hasn't
                                // been requested over the network yet.
                                view?.evaluateJavascript(MediaSniffer.COLLECT_PAGE_MEDIA_JS) { result ->
                                    runCatching {
                                        val unescaped = JSONTokener(result).nextValue() as? String ?: return@evaluateJavascript
                                        val array = JSONArray(unescaped)
                                        for (i in 0 until array.length()) {
                                            addDetected(array.optString(i))
                                        }
                                    }
                                }
                            }

                            // Runs on a worker thread for every resource the page fetches -
                            // the cheapest reliable way to spot media a site starts streaming.
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val url = request?.url?.toString()
                                if (MediaSniffer.classify(url) != null) {
                                    view?.post { addDetected(url) }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = false
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress / 100f
                            }
                        }

                        // Direct file/media links (and content-disposition responses) land here.
                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            val id = DownloadOps.enqueue(context, url, userAgent, contentDisposition, mimeType)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (id != null) "Download started - see Downloads" else "Could not start download",
                                )
                            }
                        }

                        // Long-press an image to save it via the download manager.
                        setOnLongClickListener {
                            val result = hitTestResult
                            val isImage = result.type == WebView.HitTestResult.IMAGE_TYPE ||
                                result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                            val target = result.extra
                            if (isImage && !target.isNullOrBlank() && target.startsWith("http")) {
                                pendingImageDownload = target
                                true
                            } else {
                                false
                            }
                        }

                        loadUrl(HOME_URL)
                        webViewRef = this
                    }
                },
            )
        }
    }

    if (showMediaSheet) {
        ModalBottomSheet(onDismissRequest = { showMediaSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 28.dp)) {
                Text(
                    text = "Media on this page",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    text = "Videos, audio and files this page loads. Streaming sites that use " +
                        "protected or segmented playback can't be captured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(detectedMedia.toList(), key = { it.url }) { media ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when (media.kind) {
                                    MediaKind.VIDEO -> Icons.Filled.Movie
                                    MediaKind.AUDIO -> Icons.Filled.Audiotrack
                                    MediaKind.FILE -> Icons.Filled.InsertDriveFile
                                },
                                contentDescription = media.kind.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = media.kind.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { startDownload(media.url) }) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = "Download ${media.fileName}",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingImageDownload?.let { imageUrl ->
        AlertDialog(
            onDismissRequest = { pendingImageDownload = null },
            title = { Text("Download image?") },
            text = {
                Text(
                    text = imageUrl,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImageDownload = null
                    val id = DownloadOps.enqueue(context, imageUrl, webViewRef?.settings?.userAgentString)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (id != null) "Download started - see Downloads" else "Could not start download",
                        )
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImageDownload = null }) { Text("Cancel") }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
