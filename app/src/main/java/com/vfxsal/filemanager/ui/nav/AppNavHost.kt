package com.vfxsal.filemanager.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vfxsal.filemanager.feature.clean.cleanNavGraph
import com.vfxsal.filemanager.feature.cloud.cloudNavGraph
import com.vfxsal.filemanager.feature.files.filesNavGraph
import com.vfxsal.filemanager.feature.music.musicNavGraph
import com.vfxsal.filemanager.feature.video.videoNavGraph

/**
 * Hosts every feature's nested nav graph and draws the bottom navigation bar.
 * The bar is only shown while the current destination is one of the five
 * top-level graph routes; drilling into a detail screen (file details, video
 * player, now playing, drive browser, ...) hides it automatically because
 * those routes live under a different route string within each feature graph.
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.graphRoute == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.graphRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.graphRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.FILES.graphRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            filesNavGraph(navController)
            cleanNavGraph(navController)
            videoNavGraph(navController)
            musicNavGraph(navController)
            cloudNavGraph(navController)
        }
    }
}
