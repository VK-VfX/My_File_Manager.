package com.vfxsal.filemanager.feature.wallpaper

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vfxsal.filemanager.feature.wallpaper.ui.WallpaperGalleryScreen
import com.vfxsal.filemanager.feature.wallpaper.ui.WallpaperPreviewScreen

const val WALLPAPERS_GRAPH_ROUTE = "wallpapers"
private const val PREVIEW_ROUTE = "wallpapers/preview/{designId}"

fun NavGraphBuilder.wallpapersNavGraph(navController: NavHostController) {
    composable(WALLPAPERS_GRAPH_ROUTE) {
        WallpaperGalleryScreen(
            onOpenDesign = { id -> navController.navigate("wallpapers/preview/$id") },
        )
    }

    composable(
        route = PREVIEW_ROUTE,
        arguments = listOf(navArgument("designId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val designId = backStackEntry.arguments?.getString("designId").orEmpty()
        WallpaperPreviewScreen(
            designId = designId,
            onBack = { navController.popBackStack() },
        )
    }
}
