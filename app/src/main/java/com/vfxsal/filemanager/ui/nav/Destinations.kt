package com.vfxsal.filemanager.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import com.vfxsal.filemanager.R

/**
 * Top-level, bottom-navigation-bar destinations. Each corresponds to a nested
 * nav graph contributed by its own feature package (see the `*NavGraph.kt`
 * file under each `feature` subpackage), keyed by [graphRoute] as the nested
 * graph's start route.
 */
enum class TopLevelDestination(
    val graphRoute: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    FILES(
        graphRoute = "files",
        labelRes = R.string.nav_files,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
    ),
    CLEAN(
        graphRoute = "clean",
        labelRes = R.string.nav_clean,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
    ),
    VIDEO(
        graphRoute = "video",
        labelRes = R.string.nav_video,
        selectedIcon = Icons.Filled.Movie,
        unselectedIcon = Icons.Outlined.Movie,
    ),
    MUSIC(
        graphRoute = "music",
        labelRes = R.string.nav_music,
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
    ),
    WALLPAPERS(
        graphRoute = "wallpapers",
        labelRes = R.string.nav_wallpapers,
        selectedIcon = Icons.Filled.Wallpaper,
        unselectedIcon = Icons.Outlined.Wallpaper,
    ),
    TOOLS(
        graphRoute = "tools",
        labelRes = R.string.nav_tools,
        selectedIcon = Icons.Filled.Handyman,
        unselectedIcon = Icons.Outlined.Handyman,
    ),
}
