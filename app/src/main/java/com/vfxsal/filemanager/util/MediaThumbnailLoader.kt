package com.vfxsal.filemanager.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.imageLoader

/**
 * Shared image+video-frame thumbnail loader for any list that needs to preview a file (not
 * just show a generic category icon) - e.g. Clean's junk/large/duplicate results, so the user
 * can see which photo or video they're about to delete instead of just a filename.
 *
 * Resolves the single process-wide instance built by [com.vfxsal.filemanager.FileManagerApplication]
 * instead of constructing a new one - every list row previously built its own ImageLoader,
 * which meant no shared memory cache and a fresh decode on every scroll/recomposition.
 */
@Composable
fun rememberMediaThumbnailLoader(): ImageLoader = LocalContext.current.imageLoader
