package com.vfxsal.filemanager.feature.files.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.files.components.EmptyState
import com.vfxsal.filemanager.feature.files.components.FileActionsHost
import com.vfxsal.filemanager.feature.files.components.FileListItem
import com.vfxsal.filemanager.feature.files.components.rememberFileActionsState
import com.vfxsal.filemanager.feature.files.home.categoryLabel
import com.vfxsal.filemanager.feature.files.util.FileOps
import com.vfxsal.filemanager.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    categoryName: String,
    onBack: () -> Unit,
    viewModel: CategoryViewModel = viewModel(),
) {
    val category = remember(categoryName) {
        runCatching { FileCategory.valueOf(categoryName) }.getOrDefault(FileCategory.OTHER)
    }
    LaunchedEffect(category) { viewModel.load(category) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionsState = rememberFileActionsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryLabel(category)) },
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
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.entries.isEmpty() -> EmptyState(message = "No ${categoryLabel(category).lowercase()} found")
                else -> Column {
                    Text(
                        text = "${uiState.entries.size} items · ${FormatUtils.formatFileSize(uiState.entries.sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.entries, key = { it.path }) { entry ->
                            FileListItem(
                                entry = entry,
                                selectionMode = false,
                                selected = false,
                                onClick = {
                                    if (!FileOps.tryOpen(context, entry.file)) {
                                        scope.launch { snackbarHostState.showSnackbar("No app can open this file") }
                                    }
                                },
                                onLongClick = { actionsState.showDetails(entry) },
                                onInfoClick = { actionsState.showDetails(entry) },
                            )
                        }
                    }
                }
            }

            FileActionsHost(
                state = actionsState,
                snackbarHostState = snackbarHostState,
                onChanged = { viewModel.refresh() },
            )
        }
    }
}
