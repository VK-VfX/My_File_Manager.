package com.vfxsal.filemanager.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vfxsal.filemanager.feature.clean.cleanNavGraph
import com.vfxsal.filemanager.feature.files.filesNavGraph
import com.vfxsal.filemanager.feature.music.musicNavGraph
import com.vfxsal.filemanager.feature.settings.SettingsViewModel
import com.vfxsal.filemanager.feature.tools.toolsNavGraph
import com.vfxsal.filemanager.feature.video.videoNavGraph
import com.vfxsal.filemanager.feature.wallpaper.wallpapersNavGraph
import com.vfxsal.filemanager.ui.components.OperationProgressOverlay

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
 * The bar is only shown while the current destination is one of the
 * top-level graph routes; drilling into a detail screen (file details, video
 * player, now playing, wallpaper preview, ...) hides it automatically because
 * those routes live under a different route string within each feature graph.
 */
@Composable
fun AppRoot(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.graphRoute == currentRoute }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
            ) {
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
                            icon = { NavTabIcon(destination = destination, selected = selected) },
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
            filesNavGraph(navController, settingsViewModel)
            cleanNavGraph(navController)
            videoNavGraph(navController)
            musicNavGraph(navController)
            wallpapersNavGraph(navController)
            toolsNavGraph(navController)
        }
    }

    // Batch-operation progress (bulk deletes etc.) from any tab surfaces here.
    OperationProgressOverlay()
}

/**
 * A deliberately pronounced micro-interaction, layering three simultaneous animations so the
 * tab switch reads as clearly "alive" rather than an instant icon swap: a bouncy scale pop on
 * the whole icon, a smooth color crossfade between the unselected/selected tints, and an
 * AnimatedContent crossfade+scale between the outlined and filled icon glyphs themselves.
 */
@Composable
private fun NavTabIcon(destination: TopLevelDestination, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "navIconScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(260),
        label = "navIconColor",
    )

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (
                scaleIn(
                    initialScale = 0.45f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                ) + fadeIn(tween(220))
            ).togetherWith(scaleOut(targetScale = 0.45f, animationSpec = tween(140)) + fadeOut(tween(140)))
        },
        label = "navIconShape",
        modifier = Modifier.scale(scale),
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = stringResource(destination.labelRes),
            tint = tint,
        )
    }
}
