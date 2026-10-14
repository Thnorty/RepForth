package com.repforth.feature.body

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.MeasurementKind
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The add dialog's fields, hosted in the main window rather than a dialog's. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class AddCustomFormComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a number asks for a unit label and the others do not`() {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            var kind by remember { mutableStateOf(MeasurementKind.LENGTH) }
            RepForthPreviewHost {
                AddCustomForm("", {}, kind, { kind = it }, "", {}, null)
            }
        }
        compose.onNodeWithText("Unit label").assertDoesNotExist()

        compose.onNodeWithText("Number").performClick()

        compose.onNodeWithText("Unit label").assertExists()
    }
}
