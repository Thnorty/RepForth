package com.repforth.feature.body

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.assertScreenIsAccessible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What TalkBack would find comparing two photos, and on one opened full screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class ComparePhotosAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compare_english() = check(ENGLISH, ComparePhotosScreenshotTest.PAIR)

    @Test
    fun compare_turkish() = check(TURKISH, ComparePhotosScreenshotTest.PAIR)

    @Test
    fun one_photo_english() = check(ENGLISH, ComparePhotosScreenshotTest.SINGLE)

    /** The photo names its pose and its day, and opening it leaves a way back. */
    @Test
    fun a_photo_opens_full_screen_with_a_close_button() {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { RepForthPreviewHost { ComparePhotosScreen(ComparePhotosScreenshotTest.PAIR) } }

        compose.onNodeWithContentDescription("Front photo, Sep 15, 2026").performClick()

        compose.onNodeWithContentDescription("Close").assertExists()
    }

    private fun check(locale: String, state: ComparePhotosUiState) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { RepForthPreviewHost { ComparePhotosScreen(state) } }
        compose.assertScreenIsAccessible("Compare ($locale)")
    }
}
