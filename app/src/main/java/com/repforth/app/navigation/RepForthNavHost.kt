package com.repforth.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.repforth.app.R
import androidx.navigation.toRoute
import com.repforth.feature.builder.BuilderRoute
import com.repforth.feature.builder.PlansRoute
import com.repforth.feature.exercises.ExercisesRoute
import com.repforth.feature.history.HistoryRoute
import com.repforth.feature.home.TodayRoute
import com.repforth.feature.session.SessionRoute
import com.repforth.feature.settings.SettingsRoute

/**
 * The app's single navigation graph.
 *
 * Feature screens live in their own modules; the graph stays here, because
 * something has to know about all of them and a feature module knowing about its
 * siblings is the coupling the module boundaries exist to prevent. Exercises is
 * the first real one — the rest are still placeholders.
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
            TodayRoute(
                // Null template: the session screen shows whatever is already
                // running rather than starting anything.
                onResumeWorkout = { navController.navigate(Destination.Session()) },
                onStartPlan = { planId -> navController.navigate(Destination.Session(planId)) },
                onBuildWorkout = { navController.navigate(Destination.Builder()) },
            )
        }
        composable<Destination.Plans> {
            PlansRoute(
                onNewWorkout = { navController.navigate(Destination.Builder()) },
                onEditPlan = { planId -> navController.navigate(Destination.Builder(planId)) },
                onStartPlan = { planId -> navController.navigate(Destination.Session(planId)) },
            )
        }
        composable<Destination.Session> { entry ->
            val route = entry.toRoute<Destination.Session>()
            SessionRoute(
                templateId = route.templateId,
                onExit = { navController.popBackStack() },
            )
        }
        composable<Destination.Builder> { entry ->
            val route = entry.toRoute<Destination.Builder>()
            BuilderRoute(
                planId = route.planId,
                // Saving returns to wherever the builder was opened from, which
                // is Plans today and Today tomorrow. popBackStack rather than a
                // navigate keeps that true without this knowing either.
                onSaved = { navController.popBackStack() },
            )
        }
        composable<Destination.Exercises> {
            ExercisesRoute()
        }
        composable<Destination.Progress> {
            HistoryRoute()
        }
        composable<Destination.Settings> {
            SettingsRoute()
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
