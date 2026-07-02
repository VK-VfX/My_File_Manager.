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
import com.vfxsal.filemanager.feature.files.browse.DirectoryBrowserScreen
import com.vfxsal.filemanager.feature.files.category.CategoryListScreen
import com.vfxsal.filemanager.feature.files.editor.TextEditorScreen
import com.vfxsal.filemanager.feature.files.home.FilesHomeScreen
import com.vfxsal.filemanager.feature.files.util.decodePath
import com.vfxsal.filemanager.feature.files.util.encodePath

const val FILES_GRAPH_ROUTE = "files"
private const val CATEGORY_ROUTE = "files/category/{categoryName}"
private const val BROWSE_ROUTE = "files/browse/{encodedPath}"
private const val EDIT_ROUTE = "files/edit/{encodedPath}"

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
            )
        }
    }

    composable(
        route = CATEGORY_ROUTE,
        arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
    ) { backStackEntry ->
        val categoryName = backStackEntry.arguments?.getString("categoryName") ?: FileCategory.OTHER.name
        FilesPermissionGate {
            CategoryListScreen(
                categoryName = categoryName,
                onBack = { navController.popBackStack() },
                onEditFile = { path ->
                    navController.navigate("files/edit/${encodePath(path)}")
                },
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
