package com.repforth.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.repforth.app.R
import com.repforth.app.ui.screen.PlaceholderScreen

/**
 * The app's single navigation graph.
 *
 * Feature screens will move into their own modules as they are built; the graph
 * stays here, because something has to know about all of them and a feature
 * module knowing about its siblings is the coupling the module boundaries exist
 * to prevent.
 */
@Composable
fun RepForthNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Today,
        modifier = modifier,
    ) {
        composable<Destination.Today> {
            PlaceholderScreen(titleRes = R.string.today, bodyRes = R.string.placeholder_today)
        }
        composable<Destination.Plans> {
            PlaceholderScreen(titleRes = R.string.plans, bodyRes = R.string.placeholder_plans)
        }
        composable<Destination.Exercises> {
            PlaceholderScreen(titleRes = R.string.catalog, bodyRes = R.string.placeholder_exercises)
        }
        composable<Destination.Progress> {
            PlaceholderScreen(titleRes = R.string.progress, bodyRes = R.string.placeholder_progress)
        }
        composable<Destination.Settings> {
            PlaceholderScreen(
                titleRes = R.string.settings_title,
                bodyRes = R.string.placeholder_settings,
            )
        }
    }
}

/**
 * Switches tabs the way a bottom bar is expected to behave.
 *
 * Without this, tapping tabs stacks destinations and back walks the tap history
 * instead of leaving the app. The three options together mean: never more than
 * one copy of a tab, each tab remembers its own scroll position and inner
 * back stack, and back from any tab goes to the start destination and then out.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
