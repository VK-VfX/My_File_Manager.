package com.vfxsal.filemanager.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder
import kotlinx.coroutines.launch

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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
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
