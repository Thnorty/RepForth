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
import com.repforth.app.ui.RepForthApp
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
                    RepForthApp()
                }
            }
        }
    }
}
