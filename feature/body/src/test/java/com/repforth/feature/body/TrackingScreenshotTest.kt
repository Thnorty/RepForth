package com.repforth.feature.body

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
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

/** The built-ins with their switches, and one custom type with its actions. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class TrackingScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun english() = capture("tracking-en")

    @Test
    fun turkish() = capture("tracking-tr", locale = TURKISH)

    @Test
    fun large_text() = capture("tracking-en-2x", fontScale = 2f)

    @Test
    fun turkish_large_text() = capture("tracking-tr-2x", TURKISH, 2f)

    /** The list's end, where the custom type and its two actions sit. */
    @Test
    fun custom_turkish() = capture("tracking-custom-tr", TURKISH, scrollTo = BodyFixtures.SKINFOLD.customName)

    private fun capture(name: String, locale: String = ENGLISH, fontScale: Float = 1f, scrollTo: String? = null) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { RepForthPreviewHost { TrackingScreen(TrackingUiState(BodyFixtures.TYPES)) } }
        scrollTo?.let { compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(it)) }
        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }
}
