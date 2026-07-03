package com.vfxsal.filemanager.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vfxsal.filemanager.feature.clean.cleanNavGraph
import com.vfxsal.filemanager.feature.files.filesNavGraph
import com.vfxsal.filemanager.feature.music.musicNavGraph
import com.vfxsal.filemanager.feature.video.videoNavGraph
import com.vfxsal.filemanager.feature.wallpaper.wallpapersNavGraph

/**
 * A single shared fade+subtle-slide transition applied to every destination in the app
 * (set once here at the NavHost level rather than per-screen) so both bottom-nav tab
 * switches and in-feature drill-downs get a consistent "buttery" feel without every
 * feature's nav graph needing its own transition boilerplate.
 */
private val AppEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(220, delayMillis = 40, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 10 }
}

private val AppExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(120, easing = FastOutLinearInEasing))
}

private val AppPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(220, delayMillis = 40, easing = LinearOutSlowInEasing))
}

private val AppPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(120, easing = FastOutLinearInEasing)) +
        slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 10 }
}

/**
 * Hosts every feature's nested nav graph and draws the bottom navigation bar.
 * The bar is only shown while the current destination is one of the five
 * top-level graph routes; drilling into a detail screen (file details, video
 * player, now playing, wallpaper preview, ...) hides it automatically because
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
                                AnimatedContent(
                                    targetState = selected,
                                    transitionSpec = {
                                        (
                                            scaleIn(
                                                initialScale = 0.6f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                ),
                                            ) + fadeIn(tween(150))
                                        ).togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(100)) + fadeOut(tween(100)))
                                    },
                                    label = "navIcon",
                                ) { isSelected ->
                                    Icon(
                                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = stringResource(destination.labelRes),
                                    )
                                }
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
            enterTransition = AppEnterTransition,
            exitTransition = AppExitTransition,
            popEnterTransition = AppPopEnterTransition,
            popExitTransition = AppPopExitTransition,
        ) {
            filesNavGraph(navController)
            cleanNavGraph(navController)
            videoNavGraph(navController)
            musicNavGraph(navController)
            wallpapersNavGraph(navController)
        }
    }
}
