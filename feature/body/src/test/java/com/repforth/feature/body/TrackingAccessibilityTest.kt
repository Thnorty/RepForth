package com.repforth.feature.body

import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class TrackingAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun english() = check(ENGLISH)

    @Test
    fun turkish() = check(TURKISH)

    private fun check(locale: String) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent { RepForthPreviewHost { TrackingScreen(TrackingUiState(BodyFixtures.TYPES)) } }
        compose.assertScreenIsAccessible("Tracking ($locale)")
    }
}
