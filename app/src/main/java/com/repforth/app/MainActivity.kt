package com.repforth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.repforth.app.ui.RepForthApp
import com.repforth.core.designsystem.theme.RepForthTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity. Everything below it is Compose and navigation.
 *
 * Edge-to-edge is enabled here rather than per screen (§12), so no screen has to
 * remember it and none of them can disagree.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RepForthTheme {
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
