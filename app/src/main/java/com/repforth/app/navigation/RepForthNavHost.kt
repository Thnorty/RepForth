package com.repforth.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.repforth.app.R
import com.repforth.core.designsystem.theme.rfFadeThroughEnter
import com.repforth.core.designsystem.theme.rfFadeThroughExit
import com.repforth.core.designsystem.theme.rfPopEnter
import com.repforth.core.designsystem.theme.rfPopExit
import com.repforth.core.designsystem.theme.rfPushEnter
import com.repforth.core.designsystem.theme.rfPushExit
import com.repforth.feature.builder.BuilderRoute
import com.repforth.feature.builder.PlansRoute
import com.repforth.feature.exercises.ExercisesRoute
import com.repforth.feature.history.HistoryRoute
import com.repforth.feature.home.TodayRoute
import com.repforth.feature.session.SessionRoute
import com.repforth.feature.settings.AiSettingsRoute
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
    // Resolved here, not inside the lambdas: a NavHost transition lambda is not
    // a composable scope, and these read `LocalReducedMotion` through `rfTween`.
    // Composition is the right place to read it anyway -- it is a static local,
    // so changing the setting recomposes this and rebuilds them.
    val pushEnter = rfPushEnter()
    val pushExit = rfPushExit()
    val popEnter = rfPopEnter()
    val popExit = rfPopExit()
    val fadeEnter = rfFadeThroughEnter()
    val fadeExit = rfFadeThroughExit()

    NavHost(
        navController = navController,
        startDestination = Destination.Today,
        modifier = modifier,
        // Set once for the whole graph rather than per destination, because the
        // choice is not a property of the screen being opened -- Plans leaving
        // is a fade when the user tapped another tab and a slide when they
        // opened the builder, and only the pair of endpoints knows which.
        enterTransition = { if (isPeerMove()) fadeEnter else pushEnter },
        exitTransition = { if (isPeerMove()) fadeExit else pushExit },
        popEnterTransition = { if (isPeerMove()) fadeEnter else popEnter },
        popExitTransition = { if (isPeerMove()) fadeExit else popExit },
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
                onEditWeek = { weekId ->
                    navController.navigate(Destination.Builder(weekId = weekId))
                },
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
                weekId = route.weekId,
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
            SettingsRoute(
                onOpenAiSettings = { navController.navigate(Destination.AiSettings) },
            )
        }
        composable<Destination.AiSettings> {
            AiSettingsRoute()
        }
    }
}

/**
 * Whether this move is between two bottom-bar tabs.
 *
 * Progress is not to the right of Plans in any sense a user could point at, so
 * sliding one in from the right would invent a spatial relationship that does
 * not exist; those fade through instead. Everything else — a tab opening the
 * builder, the session, or settings — has a direction and a way back, and gets
 * the shared axis.
 *
 * Settings is deliberately not in this set. It is reached from the top bar
 * rather than the bottom one, so it is a push over whichever tab was showing,
 * and back returns to that tab.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPeerMove(): Boolean =
    initialState.isTopLevel() && targetState.isTopLevel()

private fun NavBackStackEntry.isTopLevel(): Boolean =
    TopLevelDestination.entries.any { top ->
        destination.hasRoute(top.route::class)
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
