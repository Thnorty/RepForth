package com.repforth.feature.body

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Pose
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class ComparePhotosScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compare_english() = capture("compare-en") { ComparePhotosScreen(PAIR) }

    @Test
    fun compare_turkish() = capture("compare-tr", locale = TURKISH) { ComparePhotosScreen(PAIR) }

    @Test
    fun compare_large_text() = capture("compare-en-2x", fontScale = 2f) { ComparePhotosScreen(PAIR) }

    @Test
    fun compare_turkish_large_text() = capture("compare-tr-2x", TURKISH, 2f) { ComparePhotosScreen(PAIR) }

    @Test
    fun one_photo_english() = capture("compare-single-en") { ComparePhotosScreen(SINGLE) }

    private fun capture(
        name: String,
        locale: String = ENGLISH,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { RepForthPreviewHost { content() } }
        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }

    internal companion object {
        private val first = BodyFixtures.CHECK_INS.first()
        private val last = BodyFixtures.CHECK_INS.last()

        /** Front on the first and last fixture check-ins, both weighed. */
        val PAIR = ComparePhotosUiState(
            pose = Pose.FRONT,
            dates = listOf(first.date, last.date),
            left = CompareSide(first.date, "c1-front.jpg", first.measurements["weight"]),
            right = CompareSide(last.date, "c3-front.jpg", last.measurements["weight"]),
        )

        val SINGLE = ComparePhotosUiState(pose = Pose.SIDE, dates = listOf(first.date), left = null, right = null)
    }
}
