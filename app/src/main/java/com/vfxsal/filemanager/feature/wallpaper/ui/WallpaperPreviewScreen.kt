package com.vfxsal.filemanager.feature.wallpaper.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vfxsal.filemanager.feature.wallpaper.WallpaperEvent
import com.vfxsal.filemanager.ui.components.CurlyLoadingIndicator
import com.vfxsal.filemanager.feature.wallpaper.WallpaperRenderer
import com.vfxsal.filemanager.feature.wallpaper.WallpaperTarget
import com.vfxsal.filemanager.feature.wallpaper.WallpaperViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WallpaperPreviewScreen(
    designId: String,
    onBack: () -> Unit,
    viewModel: WallpaperViewModel = viewModel(),
) {
    val design = remember(designId) { viewModel.designFor(designId) }
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // WRITE_EXTERNAL_STORAGE is only declared (and only needed) up to API 29; Q+ MediaStore
    // inserts into an app's own collection need no permission at all.
    val writePermissionState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        null
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is WallpaperEvent.Applied -> when (event.target) {
                    WallpaperTarget.HOME -> "Wallpaper set for Home screen"
                    WallpaperTarget.LOCK -> "Wallpaper set for Lock screen"
                    WallpaperTarget.BOTH -> "Wallpaper set for Home and Lock screens"
                }
                WallpaperEvent.Saved -> "Saved to Pictures/WhatFiles Wallpapers"
                is WallpaperEvent.Error -> event.message
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (design == null) {
            Text(
                text = "Wallpaper not found",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val previewWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val previewHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

            val previewBitmap by produceState<ImageBitmap?>(initialValue = null, design) {
                value = withContext(Dispatchers.Default) {
                    WallpaperRenderer.render(design, previewWidthPx, previewHeightPx).asImageBitmap()
                }
            }

            val bitmap = previewBitmap
            if (bitmap == null) {
                CurlyLoadingIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            } else {
                Image(
                    bitmap = bitmap,
                    contentDescription = design.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))),
                    )
                    .padding(20.dp),
            ) {
                Column {
                    Text(design.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WallpaperActionButton(
                            label = "Home",
                            modifier = Modifier.weight(1f),
                            enabled = !isBusy,
                            onClick = { viewModel.apply(design, WallpaperTarget.HOME) },
                        )
                        WallpaperActionButton(
                            label = "Lock",
                            modifier = Modifier.weight(1f),
                            enabled = !isBusy,
                            onClick = { viewModel.apply(design, WallpaperTarget.LOCK) },
                        )
                        WallpaperActionButton(
                            label = "Both",
                            modifier = Modifier.weight(1f),
                            enabled = !isBusy,
                            onClick = { viewModel.apply(design, WallpaperTarget.BOTH) },
                        )
                        IconButton(
                            enabled = !isBusy,
                            onClick = {
                                if (writePermissionState != null && !writePermissionState.status.isGranted) {
                                    writePermissionState.launchPermissionRequest()
                                } else {
                                    viewModel.save(design)
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Save to gallery", tint = Color.White)
                        }
                    }
                }
            }
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        if (isBusy) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CurlyLoadingIndicator(color = Color.White)
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun WallpaperActionButton(label: String, modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label)
    }
}
