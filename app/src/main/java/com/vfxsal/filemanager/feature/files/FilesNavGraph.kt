package com.vfxsal.filemanager.feature.files

import android.os.Environment
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.feature.apps.InstalledAppsScreen
import com.vfxsal.filemanager.feature.files.about.AboutScreen
import com.vfxsal.filemanager.feature.files.browse.DirectoryBrowserScreen
import com.vfxsal.filemanager.feature.files.category.CategoryListScreen
import com.vfxsal.filemanager.feature.files.editor.TextEditorScreen
import com.vfxsal.filemanager.feature.files.home.FilesHomeScreen
import com.vfxsal.filemanager.feature.files.search.GlobalSearchScreen
import com.vfxsal.filemanager.feature.files.storage.StorageBreakdownScreen
import com.vfxsal.filemanager.feature.files.timeline.TimelineScreen
import com.vfxsal.filemanager.feature.files.trash.TrashScreen
import com.vfxsal.filemanager.feature.files.util.decodePath
import com.vfxsal.filemanager.feature.files.util.encodePath
import com.vfxsal.filemanager.feature.files.vault.VaultScreen

const val FILES_GRAPH_ROUTE = "files"
private const val CATEGORY_ROUTE = "files/category/{categoryName}"
private const val BROWSE_ROUTE = "files/browse/{encodedPath}"
private const val EDIT_ROUTE = "files/edit/{encodedPath}"
private const val SEARCH_ROUTE = "files/search"
private const val TRASH_ROUTE = "files/trash"
private const val STORAGE_ROUTE = "files/storage"
private const val ABOUT_ROUTE = "files/about"
private const val INSTALLED_APPS_ROUTE = "files/apps"
private const val VAULT_ROUTE = "files/vault"
private const val TIMELINE_ROUTE = "files/timeline"

fun NavGraphBuilder.filesNavGraph(navController: NavHostController) {
    composable(FILES_GRAPH_ROUTE) {
        FilesPermissionGate {
            FilesHomeScreen(
                onOpenCategory = { category ->
                    navController.navigate("files/category/${category.name}")
                },
                onOpenDirectory = { path ->
                    navController.navigate("files/browse/${encodePath(path)}")
                },
                onEditFile = { path ->
                    navController.navigate("files/edit/${encodePath(path)}")
                },
                onOpenSearch = { navController.navigate(SEARCH_ROUTE) },
                onOpenTrash = { navController.navigate(TRASH_ROUTE) },
                onOpenStorageBreakdown = { navController.navigate(STORAGE_ROUTE) },
                onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                onOpenVault = { navController.navigate(VAULT_ROUTE) },
                onOpenTimeline = { navController.navigate(TIMELINE_ROUTE) },
            )
        }
    }

    composable(VAULT_ROUTE) {
        FilesPermissionGate {
            VaultScreen(onBack = { navController.popBackStack() })
        }
    }

    composable(TIMELINE_ROUTE) {
        FilesPermissionGate {
            TimelineScreen(
                onBack = { navController.popBackStack() },
                onEditFile = { path -> navController.navigate("files/edit/${encodePath(path)}") },
            )
        }
    }

    composable(SEARCH_ROUTE) {
        FilesPermissionGate {
            GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenDirectory = { path -> navController.navigate("files/browse/${encodePath(path)}") },
                onEditFile = { path -> navController.navigate("files/edit/${encodePath(path)}") },
            )
        }
    }

    composable(TRASH_ROUTE) {
        FilesPermissionGate {
            TrashScreen(onBack = { navController.popBackStack() })
        }
    }

    composable(STORAGE_ROUTE) {
        FilesPermissionGate {
            StorageBreakdownScreen(onBack = { navController.popBackStack() })
        }
    }

    composable(ABOUT_ROUTE) {
        AboutScreen(onBack = { navController.popBackStack() })
    }

    composable(INSTALLED_APPS_ROUTE) {
        FilesPermissionGate {
            InstalledAppsScreen(onBack = { navController.popBackStack() })
        }
    }

    composable(
        route = CATEGORY_ROUTE,
        arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
    ) { backStackEntry ->
        val categoryName = backStackEntry.arguments?.getString("categoryName") ?: FileCategory.OTHER.name
        val filesGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(FILES_GRAPH_ROUTE) }
        val clipboardViewModel: ClipboardViewModel = viewModel(filesGraphEntry)
        FilesPermissionGate {
            CategoryListScreen(
                categoryName = categoryName,
                onBack = { navController.popBackStack() },
                onEditFile = { path ->
                    navController.navigate("files/edit/${encodePath(path)}")
                },
                clipboardViewModel = clipboardViewModel,
                onOpenInstalledApps = { navController.navigate(INSTALLED_APPS_ROUTE) },
            )
        }
    }

    composable(
        route = BROWSE_ROUTE,
        arguments = listOf(navArgument("encodedPath") { type = NavType.StringType }),
    ) { backStackEntry ->
        val encodedPath = backStackEntry.arguments?.getString("encodedPath").orEmpty()
        val path = decodePath(encodedPath)
        val filesGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(FILES_GRAPH_ROUTE) }
        val clipboardViewModel: ClipboardViewModel = viewModel(filesGraphEntry)
        FilesPermissionGate {
            DirectoryBrowserScreen(
                path = path,
                rootPath = Environment.getExternalStorageDirectory().absolutePath,
                onNavigate = { newPath -> navController.navigate("files/browse/${encodePath(newPath)}") },
                onBack = { navController.popBackStack() },
                onEditFile = { editPath -> navController.navigate("files/edit/${encodePath(editPath)}") },
                clipboardViewModel = clipboardViewModel,
            )
        }
    }

    composable(
        route = EDIT_ROUTE,
        arguments = listOf(navArgument("encodedPath") { type = NavType.StringType }),
    ) { backStackEntry ->
        val encodedPath = backStackEntry.arguments?.getString("encodedPath").orEmpty()
        TextEditorScreen(
            path = decodePath(encodedPath),
            onBack = { navController.popBackStack() },
        )
    }
}
