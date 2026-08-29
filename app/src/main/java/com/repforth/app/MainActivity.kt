package com.repforth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.repforth.app.ui.RepForthApp
import com.repforth.feature.onboarding.OnboardingRoute
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.designsystem.theme.RepForthTheme
import com.repforth.core.model.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single activity. Everything below it is Compose and navigation.
 *
 * Edge-to-edge is enabled here rather than per screen (§12), so no screen has to
 * remember it and none of them can disagree.
 *
 * Onboarding is chosen here rather than inside the navigation graph because it
 * is not a destination: it has no tab, no back stack, and no way out except
 * finishing. Putting it in the graph would make it somewhere you could navigate
 * back to after answering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferencesDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Seeded with the defaults rather than null, so the first frame is
            // already the right theme instead of flashing and correcting itself.
            val preferences by userPreferences.preferences
                .collectAsState(initial = UserPreferences.Default)

            RepForthTheme(
                darkTheme = preferences.themeMode.isDark(isSystemInDarkTheme()),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val appViewModel: AppViewModel = viewModel()
                    val appState by appViewModel.uiState.collectAsStateWithLifecycle()

                    when (appState) {
                        // Nothing, on purpose. The window is already painted
                        // rf_launch_background, so an empty frame here is the
                        // launch screen continuing rather than a blank flash —
                        // and a spinner for a local database read would be
                        // longer-lived than the read itself.
                        AppUiState.Loading -> Unit

                        AppUiState.Onboarding -> OnboardingRoute()

                        AppUiState.Ready -> RepForthApp()
                    }
                }
            }
        }
    }
}
