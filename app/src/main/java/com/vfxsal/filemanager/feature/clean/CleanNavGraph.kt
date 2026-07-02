package com.vfxsal.filemanager.feature.clean

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val CLEAN_GRAPH_ROUTE = "clean"

fun NavGraphBuilder.cleanNavGraph(navController: NavHostController) {
    composable(CLEAN_GRAPH_ROUTE) {
        Box(Modifier.fillMaxSize()) { Text("Clean - placeholder") }
    }
}
