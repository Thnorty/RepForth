package com.repforth.feature.body

import androidx.compose.runtime.Composable
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

/** One measurement over time with its entries, and the list of every check-in. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class BodyListsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun measurement_english() = capture("measurement-en") { Detail() }

    @Test
    fun measurement_turkish() = capture("measurement-tr", locale = TURKISH) { Detail() }

    @Test
    fun measurement_large_text() = capture("measurement-en-2x", fontScale = 2f) { Detail() }

    @Test
    fun measurement_turkish_large_text() = capture("measurement-tr-2x", TURKISH, 2f) { Detail() }

    @Test
    fun check_ins_english() = capture("check-ins-en") { List() }

    @Test
    fun check_ins_turkish_large_text() = capture("check-ins-tr-2x", TURKISH, 2f) { List() }

    @Composable
    private fun Detail() = MeasurementDetailScreen(state = MeasurementDetailFixtures.WAIST)

    @Composable
    private fun List() = CheckInListScreen(rows = checkInRows(BodyFixtures.CHECK_INS))

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
}
