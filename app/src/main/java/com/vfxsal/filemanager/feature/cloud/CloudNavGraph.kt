package com.vfxsal.filemanager.feature.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.vfxsal.filemanager.feature.cloud.ui.CloudHomeScreen
import com.vfxsal.filemanager.feature.cloud.ui.DriveBrowserScreen
import com.vfxsal.filemanager.feature.cloud.ui.PhotosImportScreen

const val CLOUD_GRAPH_ROUTE = "cloud"
private const val CLOUD_DRIVE_ROUTE = "cloud/drive"
private const val CLOUD_PHOTOS_ROUTE = "cloud/photos"

/**
 * Scopes a [CloudViewModel] to the "cloud" home entry so sign-in / browsing state survives
 * navigating between the home, Drive browser, since all of these routes stay on the back
 * stack together (none of them pop CLOUD_GRAPH_ROUTE off before pushing the next one).
 */
@Composable
private fun sharedCloudViewModel(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
): CloudViewModel {
    val parentEntry = remember(backStackEntry) {
        navController.getBackStackEntry(CLOUD_GRAPH_ROUTE)
    }
    return viewModel(viewModelStoreOwner = parentEntry)
}

fun NavGraphBuilder.cloudNavGraph(navController: NavHostController) {
    composable(CLOUD_GRAPH_ROUTE) { backStackEntry ->
        val viewModel = sharedCloudViewModel(navController, backStackEntry)
        CloudHomeScreen(
            viewModel = viewModel,
            onOpenDrive = { navController.navigate(CLOUD_DRIVE_ROUTE) },
            onOpenPhotos = { navController.navigate(CLOUD_PHOTOS_ROUTE) },
        )
    }
    composable(CLOUD_DRIVE_ROUTE) { backStackEntry ->
        val viewModel = sharedCloudViewModel(navController, backStackEntry)
        DriveBrowserScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
        )
    }
    composable(CLOUD_PHOTOS_ROUTE) {
        PhotosImportScreen(onBack = { navController.popBackStack() })
    }
}
