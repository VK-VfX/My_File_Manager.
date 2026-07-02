package com.vfxsal.filemanager.feature.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val MUSIC_GRAPH_ROUTE = "music"

fun NavGraphBuilder.musicNavGraph(navController: NavHostController) {
    composable(MUSIC_GRAPH_ROUTE) {
        Box(Modifier.fillMaxSize()) { Text("Music - placeholder") }
    }
}
