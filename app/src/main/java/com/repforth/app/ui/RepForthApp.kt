package com.repforth.app.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.repforth.app.R
import com.repforth.app.navigation.Destination
import com.repforth.app.navigation.RepForthNavHost
import com.repforth.app.navigation.TopLevelDestination
import com.repforth.app.navigation.navigateToTopLevel
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.feature.session.WorkoutStartViewModel

/**
 * The app shell: the frame every screen lands inside.
 *
 * Four tabs and a settings action, exactly the structure §12 fixes. Screens are
 * placeholders for now — building the frame once is cheaper than retrofitting
 * navigation around four finished features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepForthApp(
    navController: NavHostController = rememberNavController(),
    starter: WorkoutStartViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // The bottom bar belongs to the four tabs. On Settings, which is reached
    // from the top bar, it would offer a way out that is not "back" — so it
    // goes away, and the up arrow is the only exit.
    val currentTopLevel = TopLevelDestination.entries
        .firstOrNull { currentDestination.isOn(it) }

    // A running workout titles the bar with its own name. "Workout" is true of
    // every one of them and tells someone glancing down nothing they did not
    // already know; the plan's name is the one thing on this screen that says
    // which workout this is.
    val activeWorkoutName by starter.activeWorkoutName.collectAsStateWithLifecycle()

    Scaffold(
        // The bottom edge is the app's to defend. enableEdgeToEdge() turns off
        // decor-fits-system-windows, so the window is no longer resized when the
        // keyboard opens, and nothing else moves out of its way: a footer pinned
        // to the bottom of a screen is simply drawn underneath the IME.
        //
        // Found on a device. The builder's Save button sat behind the keyboard
        // the whole time the name field was focused — enabled, invisible, and
        // untappable — so a workout could be built and never saved. With this,
        // the footer measures to y=1285 instead of y=2162 while the keyboard is
        // up, which is above it.
        //
        // safeDrawing rather than imePadding(): it is the union of the IME, the
        // navigation bar and the display cutout, so the same line also covers a
        // device that reserves the bottom for three-button navigation. This one
        // does not — it uses gesture navigation and reserves nothing — so
        // imePadding() alone would have looked equally correct here and been
        // wrong on a phone that does. Bottom only: the top bar draws its own
        // status bar inset, and taking it here would strip the colour from
        // behind the clock.
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        ),
        topBar = {
            TopAppBar(
                title = { Text(barTitle(currentDestination, currentTopLevel, activeWorkoutName)) },
                navigationIcon = {
                    if (currentTopLevel == null) {
                        // Dispatched as a back press rather than navigateUp().
                        //
                        // A screen that intercepts back — the running workout,
                        // which asks before ending — was intercepting only the
                        // gesture, so this arrow walked straight past the
                        // question and out of the workout. Going through the
                        // dispatcher means one way back, and any screen that
                        // wants to handle it handles both.
                        IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                            Icon(
                                painter = RfIcons.Back,
                                contentDescription = stringResource(R.string.nav_back),
                            )
                        }
                    }
                },
                actions = {
                    if (currentTopLevel != null) {
                        IconButton(onClick = { navController.navigate(Destination.Settings) }) {
                            Icon(
                                painter = RfIcons.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentTopLevel == destination,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    painter = destination.icon,
                                    // Null, not the label: the label is already
                                    // visible below, and a description here makes
                                    // TalkBack read every tab name twice.
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        RepForthNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            // The same instance the bar reads, so the name in the title and the
            // name in the conflict dialog cannot disagree.
            starter = starter,
        )
    }
}

/**
 * Matches by route type rather than by string, and walks the hierarchy so a
 * nested graph still reports the tab it belongs to.
 */
private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/**
 * The app bar title for wherever we are.
 *
 * Every destination is named explicitly rather than falling back to Settings.
 * The fallback worked only while Settings was the single non-tab destination;
 * the moment the builder was added it titled itself "Settings", which is the
 * failure mode of a default that happens to be right once.
 */
/**
 * The app bar's text.
 *
 * Everything is a string resource except the running workout, which is titled
 * with the plan it came from.
 */
@Composable
private fun barTitle(
    destination: NavDestination?,
    topLevel: TopLevelDestination?,
    activeWorkoutName: String?,
): String {
    val onSession = destination?.hasRoute(Destination.Session::class) == true
    if (onSession && topLevel == null && activeWorkoutName != null) return activeWorkoutName
    return stringResource(destination.titleRes(topLevel))
}

@StringRes
private fun NavDestination?.titleRes(topLevel: TopLevelDestination?): Int = when {
    topLevel != null -> topLevel.labelRes
    this?.hasRoute(Destination.Builder::class) == true -> R.string.nav_builder
    // Replaced by the workout's own name when there is one -- see `barTitle`.
    // This remains the title for a workout started from no plan, and the branch
    // NavigationStructureTest looks for.
    this?.hasRoute(Destination.Session::class) == true -> R.string.nav_session
    this?.hasRoute(Destination.AiSettings::class) == true -> R.string.nav_ai_settings
    this?.hasRoute(Destination.Settings::class) == true -> R.string.settings_title
    // Unreachable while every destination above is named, which
    // NavigationStructureTest is what keeps true. Settings rather than a blank
    // bar, because a screen with no title looks broken and this one cannot.
    else -> R.string.settings_title
}
