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
import com.vfxsal.filemanager.feature.browser.BrowserScreen
import com.vfxsal.filemanager.feature.browser.DownloadsScreen
import com.vfxsal.filemanager.feature.files.about.AboutScreen
import com.vfxsal.filemanager.feature.files.browse.DirectoryBrowserScreen
import com.vfxsal.filemanager.feature.files.browse.SortBy
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
import com.vfxsal.filemanager.feature.files.viewer.ImageViewerScreen
import com.vfxsal.filemanager.feature.files.viewer.ImageViewerSource
import com.vfxsal.filemanager.feature.settings.SettingsScreen
import com.vfxsal.filemanager.feature.settings.SettingsViewModel

const val FILES_GRAPH_ROUTE = "files"
private const val CATEGORY_ROUTE = "files/category/{categoryName}"
private const val BROWSE_ROUTE = "files/browse/{encodedPath}"
private const val EDIT_ROUTE = "files/edit/{encodedPath}"
private const val VIEWER_ROUTE = "files/viewer/{encodedPath}?source={source}&sortBy={sortBy}&ascending={ascending}"
private const val SEARCH_ROUTE = "files/search"
private const val TRASH_ROUTE = "files/trash"
private const val STORAGE_ROUTE = "files/storage"
private const val ABOUT_ROUTE = "files/about"
private const val SETTINGS_ROUTE = "files/settings"
private const val INSTALLED_APPS_ROUTE = "files/apps"
private const val VAULT_ROUTE = "files/vault"
private const val TIMELINE_ROUTE = "files/timeline"
private const val WEB_BROWSER_ROUTE = "files/webbrowser"
private const val WEB_DOWNLOADS_ROUTE = "files/webdownloads"

private fun viewerRoute(path: String, source: String, sortBy: SortBy, ascending: Boolean): String =
    "files/viewer/${encodePath(path)}?source=$source&sortBy=${sortBy.name}&ascending=$ascending"

fun NavGraphBuilder.filesNavGraph(navController: NavHostController, settingsViewModel: SettingsViewModel) {
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
                onOpenImage = { path -> navController.navigate(viewerRoute(path, "folder", SortBy.DATE, false)) },
                onOpenSearch = { navController.navigate(SEARCH_ROUTE) },
                onOpenTrash = { navController.navigate(TRASH_ROUTE) },
                onOpenStorageBreakdown = { navController.navigate(STORAGE_ROUTE) },
                onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onOpenVault = { navController.navigate(VAULT_ROUTE) },
                onOpenTimeline = { navController.navigate(TIMELINE_ROUTE) },
                onOpenBrowser = { navController.navigate(WEB_BROWSER_ROUTE) },
            )
        }
    }

    composable(WEB_BROWSER_ROUTE) {
        BrowserScreen(
            onBack = { navController.popBackStack() },
            onOpenDownloads = { navController.navigate(WEB_DOWNLOADS_ROUTE) },
        )
    }

    composable(WEB_DOWNLOADS_ROUTE) {
        DownloadsScreen(onBack = { navController.popBackStack() })
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
                onOpenImage = { path -> navController.navigate(viewerRoute(path, "category", SortBy.DATE, false)) },
            )
        }
    }

    composable(SEARCH_ROUTE) {
        FilesPermissionGate {
            GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenDirectory = { path -> navController.navigate("files/browse/${encodePath(path)}") },
                onEditFile = { path -> navController.navigate("files/edit/${encodePath(path)}") },
                onOpenImage = { path -> navController.navigate(viewerRoute(path, "folder", SortBy.NAME, true)) },
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

    composable(SETTINGS_ROUTE) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
            settingsViewModel = settingsViewModel,
        )
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
                onOpenImage = { path, sortBy, ascending ->
                    navController.navigate(viewerRoute(path, "category", sortBy, ascending))
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
                onOpenImage = { imagePath, sortBy, ascending ->
                    navController.navigate(viewerRoute(imagePath, "folder", sortBy, ascending))
                },
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

    composable(
        route = VIEWER_ROUTE,
        arguments = listOf(
            navArgument("encodedPath") { type = NavType.StringType },
            navArgument("source") { type = NavType.StringType; defaultValue = "folder" },
            navArgument("sortBy") { type = NavType.StringType; defaultValue = "NAME" },
            navArgument("ascending") { type = NavType.BoolType; defaultValue = true },
        ),
    ) { backStackEntry ->
        val encodedPath = backStackEntry.arguments?.getString("encodedPath").orEmpty()
        val sourceArg = backStackEntry.arguments?.getString("source") ?: "folder"
        val sortByArg = backStackEntry.arguments?.getString("sortBy") ?: "NAME"
        val ascendingArg = backStackEntry.arguments?.getBoolean("ascending") ?: true
        FilesPermissionGate {
            ImageViewerScreen(
                startPath = decodePath(encodedPath),
                source = if (sourceArg == "category") ImageViewerSource.CATEGORY else ImageViewerSource.FOLDER,
                sortBy = runCatching { SortBy.valueOf(sortByArg) }.getOrDefault(SortBy.NAME),
                ascending = ascendingArg,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
