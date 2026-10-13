package com.repforth.feature.body

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_COMPARISON
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.screenshotPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Body tab: figures beside names, and a change beside each figure.
 *
 * A Turkish name at 2× beside a 28sp numeral is what would push one past the
 * other, so that is the case that has to be looked at.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class BodyScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun body_empty() = capture("body-empty-en", EMPTY)

    @Test
    fun body_english() = capture("body-en", FILLED)

    @Test
    fun body_turkish() = capture("body-tr", FILLED, locale = TURKISH)

    @Test
    fun body_large_text() = capture("body-en-2x", FILLED, fontScale = 2f)

    @Test
    fun body_turkish_large_text() = capture("body-tr-2x", FILLED, locale = TURKISH, fontScale = 2f)

    private fun capture(name: String, state: BodyUiState, locale: String = ENGLISH, fontScale: Float = 1f) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { RepForthPreviewHost { BodyScreen(state = state) } }
        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }

    private companion object {
        val EMPTY = bodyState(BodyFixtures.TYPES, emptyList())
        val FILLED = bodyState(BodyFixtures.TYPES, BodyFixtures.CHECK_INS)
    }
}
