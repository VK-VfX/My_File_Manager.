package com.vfxsal.filemanager.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val VIDEO_GRAPH_ROUTE = "video"

fun NavGraphBuilder.videoNavGraph(navController: NavHostController) {
    composable(VIDEO_GRAPH_ROUTE) {
        Box(Modifier.fillMaxSize()) { Text("Video - placeholder") }
    }
}
