package com.repforth.app.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.repforth.app.R
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.RepForthTheme
import com.repforth.core.designsystem.theme.Space

/**
 * Stands in for a screen that has not been built yet.
 *
 * One composable for all five rather than five near-identical files: they differ
 * only in two strings, and a copy per screen is the duplication this project
 * treats as a bug. Each is deleted as its feature lands.
 */
@Composable
fun PlaceholderScreen(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Scrollable even though the content is short: at 200% font scaling
            // (§12) two lines of Turkish can exceed a small phone's height, and
            // unreachable text is a worse failure than a scrollbar.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.gutterPhone, vertical = Space.s8),
        verticalArrangement = Arrangement.spacedBy(Space.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    RepForthTheme {
        PlaceholderScreen(titleRes = R.string.today, bodyRes = R.string.placeholder_today)
    }
}
