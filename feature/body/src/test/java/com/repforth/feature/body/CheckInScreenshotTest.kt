package com.repforth.feature.body

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

/** The check-in form: a field per measurement, and a slot per pose. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class CheckInScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun check_in_english() = capture("check-in-en")

    @Test
    fun check_in_turkish() = capture("check-in-tr", locale = TURKISH)

    @Test
    fun check_in_large_text() = capture("check-in-en-2x", fontScale = 2f)

    @Test
    fun check_in_turkish_large_text() = capture("check-in-tr-2x", locale = TURKISH, fontScale = 2f)

    private fun capture(name: String, locale: String = ENGLISH, fontScale: Float = 1f) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { RepForthPreviewHost { CheckInScreen(state = STATE, cameraAvailable = true) } }
        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }

    internal companion object {
        val STATE = CheckInUiState(
            loading = false,
            id = "c3",
            date = BodyFixtures.TODAY,
            today = BodyFixtures.TODAY,
            fields = BodyFixtures.TYPES.filter { it.tracked }.map { type ->
                FieldState(type, text = if (type.id == "waist") "88" else null, originalValue = BodyFixtures.CHECK_INS.last().measurements[type.id])
            },
            photos = mapOf(Pose.FRONT to "c3-front.jpg"),
            canDelete = true,
        )
    }
}
