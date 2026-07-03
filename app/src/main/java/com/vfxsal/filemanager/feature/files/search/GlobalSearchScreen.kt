package com.vfxsal.filemanager.feature.files.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.components.FileActionsHost
import com.vfxsal.filemanager.feature.files.components.FileListItem
import com.vfxsal.filemanager.feature.files.components.rememberFileActionsState
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onEditFile: (String) -> Unit,
    viewModel: GlobalSearchViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionsState = rememberFileActionsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        placeholder = { Text("Search all files") },
                    )
                },
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
            val state = when {
                uiState.query.isBlank() -> "idle"
                uiState.isSearching && uiState.results.isEmpty() -> "searching"
                uiState.results.isEmpty() -> "empty"
                else -> "results"
            }
            Crossfade(targetState = state, label = "globalSearchContent") { s ->
                when (s) {
                    "idle" -> EmptyState(message = "Search across your entire storage")
                    "searching" -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CurlyLoadingIndicator()
                    }
                    "empty" -> EmptyState(message = "No matches for \"${uiState.query}\"")
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.results, key = { it.path }) { entry ->
                            FileListItem(
                                entry = entry,
                                selectionMode = false,
                                selected = false,
                                onClick = {
                                    if (entry.isDirectory) {
                                        onOpenDirectory(entry.path)
                                    } else if (!FileOps.openOrEdit(context, entry, onEditFile)) {
                                        scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                    }
                                },
                                onLongClick = { actionsState.showDetails(entry) },
                                onInfoClick = { actionsState.showDetails(entry) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            FileActionsHost(
                state = actionsState,
                snackbarHostState = snackbarHostState,
                onChanged = { viewModel.setQuery(uiState.query) },
                onEditFile = onEditFile,
            )
        }
    }
}
