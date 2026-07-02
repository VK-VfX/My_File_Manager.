package com.vfxsal.filemanager.feature.cloud

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val CLOUD_GRAPH_ROUTE = "cloud"

fun NavGraphBuilder.cloudNavGraph(navController: NavHostController) {
    composable(CLOUD_GRAPH_ROUTE) {
        Box(Modifier.fillMaxSize()) { Text("Cloud - placeholder") }
    }
}
