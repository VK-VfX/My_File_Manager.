package com.vfxsal.filemanager.feature.clean

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.vfxsal.filemanager.feature.clean.dashboard.CleanDashboardScreen
import com.vfxsal.filemanager.feature.clean.duplicates.DuplicateFilesScreen
import com.vfxsal.filemanager.feature.clean.junk.JunkFilesScreen
import com.vfxsal.filemanager.feature.clean.large.LargeFilesScreen
import com.vfxsal.filemanager.feature.clean.similar.SimilarPhotosScreen

const val CLEAN_GRAPH_ROUTE = "clean"
private const val CLEAN_JUNK_ROUTE = "clean/junk"
private const val CLEAN_LARGE_ROUTE = "clean/large"
private const val CLEAN_DUPLICATES_ROUTE = "clean/duplicates"
private const val CLEAN_SIMILAR_PHOTOS_ROUTE = "clean/similar_photos"

fun NavGraphBuilder.cleanNavGraph(navController: NavHostController) {
    composable(CLEAN_GRAPH_ROUTE) {
        CleanDashboardScreen(
            onNavigateJunk = { navController.navigate(CLEAN_JUNK_ROUTE) },
            onNavigateLarge = { navController.navigate(CLEAN_LARGE_ROUTE) },
            onNavigateDuplicates = { navController.navigate(CLEAN_DUPLICATES_ROUTE) },
            onNavigateSimilarPhotos = { navController.navigate(CLEAN_SIMILAR_PHOTOS_ROUTE) },
        )
    }
    composable(CLEAN_JUNK_ROUTE) {
        JunkFilesScreen(onBack = { navController.popBackStack() })
    }
    composable(CLEAN_LARGE_ROUTE) {
        LargeFilesScreen(onBack = { navController.popBackStack() })
    }
    composable(CLEAN_DUPLICATES_ROUTE) {
        DuplicateFilesScreen(onBack = { navController.popBackStack() })
    }
    composable(CLEAN_SIMILAR_PHOTOS_ROUTE) {
        SimilarPhotosScreen(onBack = { navController.popBackStack() })
    }
}
