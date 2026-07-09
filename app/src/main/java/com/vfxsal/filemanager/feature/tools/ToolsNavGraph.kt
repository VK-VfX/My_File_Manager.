package com.vfxsal.filemanager.feature.tools

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.vfxsal.filemanager.feature.tools.archive.ArchiveManagerScreen
import com.vfxsal.filemanager.feature.tools.dualpane.DualPaneScreen
import com.vfxsal.filemanager.feature.tools.viewer.QuickViewerScreen

const val TOOLS_GRAPH_ROUTE = "tools"
private const val DUAL_PANE_ROUTE = "tools/dualpane"
private const val ARCHIVE_ROUTE = "tools/archive"
private const val QUICK_VIEWER_ROUTE = "tools/viewer"

fun NavGraphBuilder.toolsNavGraph(navController: NavHostController) {
    composable(TOOLS_GRAPH_ROUTE) {
        ToolsHomeScreen(
            onOpenDualPane = { navController.navigate(DUAL_PANE_ROUTE) },
            onOpenArchive = { navController.navigate(ARCHIVE_ROUTE) },
            onOpenQuickViewer = { navController.navigate(QUICK_VIEWER_ROUTE) },
        )
    }

    composable(DUAL_PANE_ROUTE) {
        DualPaneScreen(onBack = { navController.popBackStack() })
    }

    composable(ARCHIVE_ROUTE) {
        ArchiveManagerScreen(onBack = { navController.popBackStack() })
    }

    composable(QUICK_VIEWER_ROUTE) {
        QuickViewerScreen(onBack = { navController.popBackStack() })
    }
}
