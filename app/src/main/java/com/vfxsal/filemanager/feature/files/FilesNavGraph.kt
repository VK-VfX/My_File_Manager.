package com.vfxsal.filemanager.feature.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val FILES_GRAPH_ROUTE = "files"

fun NavGraphBuilder.filesNavGraph(navController: NavHostController) {
    composable(FILES_GRAPH_ROUTE) {
        Box(Modifier.fillMaxSize()) { Text("Files - placeholder") }
    }
}
