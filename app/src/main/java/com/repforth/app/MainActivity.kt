package com.repforth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.RepForthTheme
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RepForthTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TokenProof()
                }
            }
        }
    }
}

/**
 * Phase 0 smoke screen: renders the ported tokens so the design system can be
 * eyeballed on a real device. Replaced by the Exercises catalog once
 * core:exercise-data lands.
 */
@Composable
private fun TokenProof() {
    val ext = RepForthTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Layout.gutterPhone),
        verticalArrangement = Arrangement.spacedBy(Layout.sectionGap),
    ) {
        Text(
            text = "RepForth",
            style = MaterialTheme.typography.headlineMedium,
            color = ext.textStrong,
        )

        // Numbers are the hero.
        Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
            Text(
                text = "SET 3 OF 4",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("82.5", style = RepForthNumeric.xl, color = ext.numeric)
                Text(
                    text = "kg",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.s1, bottom = Space.s2),
                )
            }
            Text(
                text = "× 8 reps · last time 80 kg × 8",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Surface elevation is carried by tone, not shadow.
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(
                "SURFACES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                listOf(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                ).forEach { tone ->
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(tone),
                    )
                }
            }
        }

        // Semantic roles, including `info` which M3 has no slot for.
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(
                "SEMANTIC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Swatch("accent", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                Swatch("rest", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                Swatch("done", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                Swatch("info", ext.infoContainer, ext.onInfoContainer)
                Swatch("error", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // The one lime action, at the 64dp in-session target.
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(Target.session),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Start workout", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Swatch(label: String, fill: Color, on: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = on,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101408, heightDp = 900)
@Composable
private fun TokenProofPreview() {
    RepForthTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) { TokenProof() }
    }
}
