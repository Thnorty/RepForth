package com.repforth.feature.body

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class BodyScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Someone who wants to track their arms should not have to save a first
     * check-in of weight, body fat and waist before they can say so. Found by
     * `BodyFlowTest` on the emulator, which opened an empty tab and found no way in.
     */
    @Test
    fun `the empty tab still offers choosing what to track`() {
        var chose = 0
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            RepForthPreviewHost {
                BodyScreen(state = BodyUiState(loading = false), onChooseTracked = { chose++ })
            }
        }

        compose.onNodeWithText("Choose what to track").performClick()

        assertEquals(1, chose)
    }
}
