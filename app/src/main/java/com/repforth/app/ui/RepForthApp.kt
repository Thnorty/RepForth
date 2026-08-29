package com.repforth.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
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
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bottom bar belongs to the four tabs. On Settings, which is reached
    // from the top bar, it would offer a way out that is not "back" — so it
    // goes away, and the up arrow is the only exit.
    val currentTopLevel = TopLevelDestination.entries
        .firstOrNull { currentDestination.isOn(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(currentDestination.titleRes(currentTopLevel))) },
                navigationIcon = {
                    if (currentTopLevel == null) {
                        IconButton(onClick = { navController.navigateUp() }) {
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
@StringRes
private fun NavDestination?.titleRes(topLevel: TopLevelDestination?): Int = when {
    topLevel != null -> topLevel.labelRes
    this?.hasRoute(Destination.Builder::class) == true -> R.string.nav_builder
    else -> R.string.settings_title
}
