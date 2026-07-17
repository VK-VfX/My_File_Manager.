package com.vfxsal.filemanager.feature.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vfxsal.filemanager.util.FormatUtils
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

private const val HOME_URL = "https://www.google.com"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

/** Turns address-bar input into a URL: full URLs pass through, bare domains get https://,
 *  and anything that doesn't look like a host becomes a Google web search. */
internal fun normalizeInput(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
    }
}

/** One open browser tab. Only the active tab's [WebView] is live; backgrounded tabs keep their
 *  navigation history in [webViewBundle] (via [WebView.saveState]/[WebView.restoreState]) so
 *  switching tabs is cheap and doesn't require juggling N live WebView instances. */
private class BrowserTab(initialUrl: String, val isIncognito: Boolean = false) {
    val id: String = UUID.randomUUID().toString()
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("New tab")
    var isBookmarked by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var desktopSite by mutableStateOf(false)
    var webViewBundle: Bundle? = null
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
    val isDarkTheme = isSystemInDarkTheme()
    val focusManager = LocalFocusManager.current

    val tabs = remember { mutableStateListOf(BrowserTab(HOME_URL)) }
    var activeTabId by remember { mutableStateOf(tabs.first().id) }
    fun activeTab(): BrowserTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    var addressField by remember { mutableStateOf(TextFieldValue(HOME_URL)) }
    var addressFocused by remember { mutableStateOf(false) }
    fun setAddress(url: String) {
        addressField = TextFieldValue(url, selection = TextRange(url.length))
    }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var pendingImageDownload by remember { mutableStateOf<String?>(null) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findActiveMatch by remember { mutableIntStateOf(0) }
    var findTotalMatches by remember { mutableIntStateOf(0) }

    val addressSuggestions = remember(addressField.text, addressFocused) {
        val query = addressField.text.trim()
        if (!addressFocused || query.isBlank()) {
            emptyList()
        } else {
            (BrowserStore.history(context) + BrowserStore.bookmarks(context))
                .distinctBy { it.url }
                .filter { it.url.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true) }
                .take(5)
        }
    }

    // Media spotted on the current page (network sniffing + DOM scan), newest page only.
    val detectedMedia = remember { mutableStateListOf<DetectedMedia>() }
    val addDetected: (String?, MediaKind?) -> Unit = { url, assertedKind ->
        val kind = assertedKind ?: MediaSniffer.classify(url)
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

    // Snapshots the live WebView's navigation state into the currently active tab so it can be
    // restored later - called right before that WebView gets pointed at a different tab.
    fun captureActiveTabState() {
        val tab = activeTab()
        val wv = webViewRef ?: return
        tab.url = wv.url ?: tab.url
        tab.title = wv.title?.takeIf { it.isNotBlank() } ?: tab.title
        tab.canGoBack = wv.canGoBack()
        tab.webViewBundle = Bundle().also { wv.saveState(it) }
    }

    fun activateTab(tab: BrowserTab) {
        if (tab.id != activeTabId) captureActiveTabState()
        activeTabId = tab.id
        setAddress(tab.url)
        isBookmarked = tab.isBookmarked
        canGoBack = tab.canGoBack
        detectedMedia.clear()
        showLibrary = false
        showFindBar = false
        val wv = webViewRef ?: return
        CookieManager.getInstance().setAcceptCookie(!tab.isIncognito)
        wv.settings.userAgentString = if (tab.desktopSite) DESKTOP_USER_AGENT else null
        val bundle = tab.webViewBundle
        if (bundle != null) wv.restoreState(bundle) else wv.loadUrl(tab.url)
    }

    fun addTab(url: String = HOME_URL, incognito: Boolean = false) {
        captureActiveTabState()
        val tab = BrowserTab(url, isIncognito = incognito)
        tabs.add(tab)
        activeTabId = tab.id
        setAddress(url)
        isBookmarked = false
        canGoBack = false
        detectedMedia.clear()
        showLibrary = false
        showFindBar = false
        CookieManager.getInstance().setAcceptCookie(!incognito)
        webViewRef?.settings?.userAgentString = null
        webViewRef?.loadUrl(url)
    }

    fun closeTab(tab: BrowserTab) {
        if (tabs.size == 1) {
            onBack()
            return
        }
        val idx = tabs.indexOf(tab)
        val wasActive = tab.id == activeTabId
        tabs.remove(tab)
        if (tab.isIncognito) {
            // The single shared WebView has no per-tab cookie jar, so a private tab's promise
            // to "leave no trace" is honored by wiping cookies/storage the moment it closes
            // instead of trying to isolate them while it's open.
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
        if (wasActive) {
            val next = tabs.getOrElse(idx.coerceAtMost(tabs.size - 1)) { tabs.last() }
            activeTabId = next.id
            setAddress(next.url)
            isBookmarked = next.isBookmarked
            canGoBack = next.canGoBack
            detectedMedia.clear()
            val wv = webViewRef
            if (wv != null) {
                CookieManager.getInstance().setAcceptCookie(!next.isIncognito)
                wv.settings.userAgentString = if (next.desktopSite) DESKTOP_USER_AGENT else null
                val bundle = next.webViewBundle
                if (bundle != null) wv.restoreState(bundle) else wv.loadUrl(next.url)
            }
        }
    }

    fun loadUrl(url: String) {
        showLibrary = false
        webViewRef?.loadUrl(url)
    }

    fun setDesktopSite(enabled: Boolean) {
        val tab = activeTab()
        tab.desktopSite = enabled
        val wv = webViewRef ?: return
        wv.settings.userAgentString = if (enabled) DESKTOP_USER_AGENT else null
        wv.reload()
    }

    // Handles magnet links and .torrent files: WebView has no native BitTorrent support (nor
    // does any mainstream mobile browser), so these are handed off to whatever torrent client
    // the user has installed, the same way a desktop browser would.
    fun tryOpenTorrentLink(url: String): Boolean {
        val isMagnet = url.startsWith("magnet:", ignoreCase = true)
        val isTorrentFile = url.substringBefore('?').endsWith(".torrent", ignoreCase = true)
        if (!isMagnet && !isTorrentFile) return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            scope.launch {
                snackbarHostState.showSnackbar("No torrent app installed to open this link")
            }
            true
        }
    }

    // Hardware/gesture back closes the find bar, then navigates page history, then leaves the browser.
    BackHandler(enabled = showFindBar || canGoBack) {
        when {
            showFindBar -> {
                showFindBar = false
                findQuery = ""
                findActiveMatch = 0
                findTotalMatches = 0
                webViewRef?.clearMatches()
            }
            canGoBack -> webViewRef?.goBack()
        }
    }

    Scaffold(
        topBar = {
            Column {
                BrowserTabStrip(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    onSelectTab = { tab -> activateTab(tab) },
                    onCloseTab = { tab -> closeTab(tab) },
                    onNewTab = { addTab() },
                )
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = addressField,
                            onValueChange = { addressField = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .onFocusChanged { state ->
                                    addressFocused = state.isFocused
                                    if (state.isFocused) {
                                        addressField = addressField.copy(
                                            selection = TextRange(0, addressField.text.length),
                                        )
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = {
                                Text(
                                    "Search or enter address",
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                // A tappable "Go" alongside the keyboard's own Go/Enter action -
                                // not every keyboard makes that action obviously labeled.
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    loadUrl(normalizeInput(addressField.text))
                                }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Go")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                focusManager.clearFocus()
                                loadUrl(normalizeInput(addressField.text))
                            }),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.Close, contentDescription = "Close browser")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isBookmarked = BrowserStore.toggleBookmark(context, activeTab().url, addressField.text)
                            activeTab().isBookmarked = isBookmarked
                            scope.launch {
                                snackbarHostState.showSnackbar(if (isBookmarked) "Bookmarked" else "Bookmark removed")
                            }
                        }) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Find in page") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showFindBar = true
                                        findQuery = ""
                                        findActiveMatch = 0
                                        findTotalMatches = 0
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Desktop site") },
                                    leadingIcon = { Icon(Icons.Filled.DesktopWindows, contentDescription = null) },
                                    trailingIcon = {
                                        if (activeTab().desktopSite) {
                                            Icon(Icons.Filled.Check, contentDescription = "Enabled")
                                        }
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        setDesktopSite(!activeTab().desktopSite)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("New private tab") },
                                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        addTab(incognito = true)
                                    },
                                )
                            }
                        }
                    },
                )
                if (addressFocused && addressSuggestions.isNotEmpty()) {
                    AddressSuggestions(
                        suggestions = addressSuggestions,
                        onSelect = { link ->
                            focusManager.clearFocus()
                            setAddress(link.url)
                            loadUrl(link.url)
                        },
                    )
                }
                if (showFindBar) {
                    FindInPageBar(
                        query = findQuery,
                        activeMatch = findActiveMatch,
                        totalMatches = findTotalMatches,
                        onQueryChange = { q ->
                            findQuery = q
                            if (q.isBlank()) {
                                webViewRef?.clearMatches()
                                findActiveMatch = 0
                                findTotalMatches = 0
                            } else {
                                webViewRef?.findAllAsync(q)
                            }
                        },
                        onNext = { webViewRef?.findNext(true) },
                        onPrevious = { webViewRef?.findNext(false) },
                        onClose = {
                            showFindBar = false
                            findQuery = ""
                            findActiveMatch = 0
                            findTotalMatches = 0
                            webViewRef?.clearMatches()
                        },
                    )
                }
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { pageProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
        },
        bottomBar = {
            BrowserBottomBar(
                canGoBack = canGoBack,
                isLoading = isLoading,
                mediaCount = detectedMedia.size,
                onBack = { webViewRef?.goBack() },
                onForward = { webViewRef?.goForward() },
                onReloadOrStop = { if (isLoading) webViewRef?.stopLoading() else webViewRef?.reload() },
                onHistory = { showLibrary = true },
                onMedia = { showMediaSheet = true },
                onDownloads = onOpenDownloads,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { webViewRef?.reload() },
                modifier = Modifier.fillMaxSize(),
            ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.setSupportZoom(true)
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.safeBrowsingEnabled = true

                        // Full Chromium engine features via androidx.webkit's feature-detected
                        // compat APIs, since which ones are actually available depends on the
                        // installed WebView/Chromium build (updated independently via Play
                        // Store), not just the app's minSdk.
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDarkTheme)
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                            if (isDoneCounting) {
                                findTotalMatches = numberOfMatches
                                findActiveMatch = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                showLibrary = false
                                url?.let {
                                    setAddress(it)
                                    val tab = tabs.firstOrNull { t -> t.id == activeTabId }
                                    tab?.url = it
                                    isBookmarked = BrowserStore.isBookmarked(context, it)
                                    tab?.isBookmarked = isBookmarked
                                }
                                canGoBack = view?.canGoBack() == true
                                tabs.firstOrNull { t -> t.id == activeTabId }?.canGoBack = canGoBack
                                detectedMedia.clear()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                val tab = tabs.firstOrNull { t -> t.id == activeTabId }
                                tab?.canGoBack = canGoBack
                                if (url != null && tab?.isIncognito != true) {
                                    BrowserStore.recordVisit(context, url, view?.title.orEmpty())
                                }
                                view?.title?.takeIf { it.isNotBlank() }?.let { tab?.title = it }
                                // Scan the DOM for video/audio elements whose media hasn't
                                // been requested over the network yet.
                                view?.evaluateJavascript(MediaSniffer.COLLECT_PAGE_MEDIA_JS) { result ->
                                    runCatching {
                                        val unescaped = JSONTokener(result).nextValue() as? String ?: return@evaluateJavascript
                                        for (hit in MediaSniffer.parsePageMedia(unescaped)) {
                                            addDetected(hit.url, hit.assertedKind)
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
                                    view?.post { addDetected(url, null) }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return tryOpenTorrentLink(url)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress / 100f
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    tabs.firstOrNull { t -> t.id == activeTabId }?.title = title
                                }
                            }

                            // Sites that open links via window.open()/target=_blank (OAuth
                            // popups, "open in new tab" links) get a real new tab in this
                            // app's own tab strip instead of silently doing nothing - there's
                            // only ever one live WebView, reused across tabs, so the popup's
                            // transport is handed that same instance.
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?,
                            ): Boolean {
                                if (isDialog) return false
                                addTab()
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = webViewRef
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        // Direct file/media links (and content-disposition responses) land here.
                        // .torrent downloads specifically take this path rather than
                        // shouldOverrideUrlLoading, since WebView treats them as a download, not
                        // a navigation.
                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            if (tryOpenTorrentLink(url)) return@setDownloadListener
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

            if (showLibrary) {
                BrowserLibrary(
                    bookmarks = remember(showLibrary) { BrowserStore.bookmarks(context) },
                    history = remember(showLibrary) { BrowserStore.history(context) },
                    onOpenLink = { url ->
                        setAddress(url)
                        loadUrl(url)
                    },
                    onClearHistory = { BrowserStore.clearHistory(context) },
                    onClose = { showLibrary = false },
                )
            }
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
                        MediaRow(
                            media = media,
                            userAgent = webViewRef?.settings?.userAgentString,
                            onDownload = {
                                if (media.kind == MediaKind.STREAM) {
                                    // A stream is a playlist of many segment URLs, not one file -
                                    // the system DownloadManager (used for everything else) can
                                    // only fetch a single URL, so this needs its own engine. It
                                    // runs in a process-scoped manager (not tied to this screen)
                                    // so closing the browser doesn't cancel it - progress shows in
                                    // Downloads, same as every other download.
                                    HlsDownloadManager.enqueue(
                                        context = context,
                                        manifestUrl = media.url,
                                        suggestedTitle = media.fileName.substringBeforeLast('.'),
                                        userAgent = webViewRef?.settings?.userAgentString,
                                    )
                                    showMediaSheet = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Downloading stream - see Downloads for progress")
                                    }
                                } else {
                                    startDownload(media.url)
                                }
                            },
                        )
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

/** Horizontally scrollable strip of open tabs plus a "new tab" button. Chips are sized for a
 *  thumb, not a mouse pointer - tall enough and wide enough to read a title and hit the close
 *  target without hitting the neighbouring tab. */
@Composable
private fun BrowserTabStrip(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onSelectTab: (BrowserTab) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    onNewTab: () -> Unit,
) {
    Surface(tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    BrowserTabChip(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        onClick = { onSelectTab(tab) },
                        onClose = { onCloseTab(tab) },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New tab",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BrowserTabChip(
    tab: BrowserTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 120.dp, max = 200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (tab.isIncognito) Icons.Filled.VisibilityOff else Icons.Filled.Public,
            contentDescription = if (tab.isIncognito) "Private tab" else null,
            modifier = Modifier.size(18.dp),
            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (tab.isIncognito) "Private" else tab.title.ifBlank { tab.url },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close tab",
                modifier = Modifier.size(16.dp),
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** History/bookmark matches shown under the address bar while it's focused with non-blank text. */
@Composable
private fun AddressSuggestions(suggestions: List<BrowserLink>, onSelect: (BrowserLink) -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            suggestions.forEach { link ->
                StartLinkRow(link = link, leadingIcon = Icons.Filled.History, onClick = { onSelect(link) })
            }
        }
    }
}

/** In-page text search bar, backed by [WebView.findAllAsync]/[WebView.findNext]. */
@Composable
private fun FindInPageBar(
    query: String,
    activeMatch: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).height(52.dp),
                singleLine = true,
                placeholder = { Text("Find in page") },
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            if (totalMatches > 0) {
                Text(
                    text = "$activeMatch/$totalMatches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            IconButton(onClick = onPrevious, enabled = totalMatches > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext, enabled = totalMatches > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close find bar")
            }
        }
    }
}

/** A row in the "media on this page" sheet. Format is known up front from the URL; size isn't,
 *  so it's probed with a HEAD request the first time the row is shown and cached on [media]
 *  itself (surviving sheet dismiss/reopen while the page's media list is still current). */
@Composable
private fun MediaRow(
    media: DetectedMedia,
    userAgent: String?,
    onDownload: () -> Unit,
) {
    LaunchedEffect(media.url) {
        // Playlists (HLS/DASH) don't have a single downloadable size - skip the probe.
        if (media.kind == MediaKind.STREAM || media.sizeBytes != null || media.isProbingSize) return@LaunchedEffect
        media.isProbingSize = true
        media.sizeBytes = withContext(Dispatchers.IO) { DownloadOps.probeSize(media.url, userAgent) }
        media.isProbingSize = false
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (media.kind) {
                MediaKind.VIDEO -> Icons.Filled.Movie
                MediaKind.STREAM -> Icons.Filled.Movie
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
            val subtitle = buildString {
                append(media.format)
                append(" · ")
                append(media.kind.label)
                when {
                    media.kind == MediaKind.STREAM -> Unit
                    media.sizeBytes != null -> append(" · ").append(FormatUtils.formatFileSize(media.sizeBytes!!))
                    media.isProbingSize -> append(" · sizing…")
                    else -> append(" · size unknown")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDownload) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Download ${media.fileName}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Bottom toolbar carrying the frequently-used navigation actions within thumb reach, each with
 *  a full-size touch target - the old layout crammed all of these into the address bar's row. */
@Composable
private fun BrowserBottomBar(
    canGoBack: Boolean,
    isLoading: Boolean,
    mediaCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onHistory: () -> Unit,
    onMedia: () -> Unit,
    onDownloads: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Page back", modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Page forward", modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onReloadOrStop) {
                Icon(
                    imageVector = if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = if (isLoading) "Stop loading" else "Reload page",
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(onClick = onHistory) {
                Icon(Icons.Filled.History, contentDescription = "History and bookmarks", modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onMedia, enabled = mediaCount > 0) {
                BadgedBox(
                    badge = { if (mediaCount > 0) Badge { Text("$mediaCount") } },
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = "Media found on this page", modifier = Modifier.size(26.dp))
                }
            }
            IconButton(onClick = onDownloads) {
                Icon(Icons.Filled.Download, contentDescription = "Downloads", modifier = Modifier.size(26.dp))
            }
        }
    }
}

/**
 * Full-screen bookmarks + history panel, reachable at any time from the bottom bar's History
 * button (previously this content only existed on a blank start page that the browser no longer
 * shows, which made bookmarks and history effectively unreachable).
 */
@Composable
private fun BrowserLibrary(
    bookmarks: List<BrowserLink>,
    history: List<BrowserLink>,
    onOpenLink: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val engineVersion = remember {
        runCatching { WebViewCompat.getCurrentWebViewPackage(context)?.versionName }.getOrNull()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to page")
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "History & bookmarks",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (engineVersion != null) {
                        Text(
                            text = "Powered by Chromium $engineVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                if (bookmarks.isNotEmpty()) {
                    item {
                        StartSectionHeader(
                            icon = Icons.Filled.Bookmark,
                            title = "Bookmarks",
                        )
                    }
                    items(bookmarks, key = { "bm_" + it.url }) { link ->
                        StartLinkRow(
                            link = link,
                            leadingIcon = Icons.Filled.Language,
                            onClick = { onOpenLink(link.url) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                if (history.isNotEmpty()) {
                    item {
                        StartSectionHeader(
                            icon = Icons.Filled.History,
                            title = "Recent",
                            trailing = {
                                TextButton(onClick = onClearHistory) { Text("Clear") }
                            },
                        )
                    }
                    items(history, key = { "hs_" + it.url }) { link ->
                        StartLinkRow(
                            link = link,
                            leadingIcon = Icons.Filled.History,
                            onClick = { onOpenLink(link.url) },
                        )
                    }
                }

                if (bookmarks.isEmpty() && history.isEmpty()) {
                    item {
                        Text(
                            text = "No bookmarks or history yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun StartLinkRow(
    link: BrowserLink,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title.ifBlank { link.host },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = link.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
