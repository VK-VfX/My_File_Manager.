package com.vfxsal.filemanager.feature.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vfxsal.filemanager.util.PermissionUtils

/**
 * MANAGE_EXTERNAL_STORAGE can only be granted through a dedicated Settings screen
 * (no runtime prompt exists for it), so this gate shows a rationale + button that
 * launches that screen, then re-checks the grant on every ON_RESUME - covering both
 * the "granted in Settings and came back" and "revoked while backgrounded" cases.
 */
@Composable
fun FilesPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(PermissionUtils.hasAllFilesAccess(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasAccess = PermissionUtils.hasAllFilesAccess(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = PermissionUtils.hasAllFilesAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AnimatedContent(
        targetState = hasAccess,
        transitionSpec = {
            (fadeIn(tween(250)) togetherWith fadeOut(tween(150)))
        },
        label = "filesPermissionGate",
    ) { granted ->
        if (granted) {
            content()
        } else {
            PermissionRationaleScreen(onGrantClick = { launcher.launch(PermissionUtils.allFilesAccessIntent(context)) })
        }
    }
}

@Composable
private fun PermissionRationaleScreen(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Full storage access needed",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "WhatFiles? needs access to all files on this device to browse, organize, " +
                "copy, move and clean up your folders. Grant \"Allow access to manage all files\" " +
                "on the next screen to continue.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrantClick) {
            Text("Grant access")
        }
    }
}
