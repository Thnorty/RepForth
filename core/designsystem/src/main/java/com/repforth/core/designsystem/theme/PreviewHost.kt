package com.repforth.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/**
 * A screen on its own, with the ground under it that the app normally supplies.
 *
 * In the running app every screen sits inside a `Scaffold`, and the `Scaffold`
 * is what paints the background. A screen rendered without one — in a `@Preview`
 * or a screenshot test — therefore draws its dark-theme text onto whatever the
 * host window happens to be, which is white. The first screenshot recorded of
 * Settings came out as pale green text on pale grey, and the bug was in the
 * harness rather than in anything a user could reach.
 *
 * Public and in `main` rather than a test fixture because previews need exactly
 * the same thing, and a preview that lies about contrast is the same failure by
 * a different route.
 */
@Composable
fun RepForthPreviewHost(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    RepForthTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
