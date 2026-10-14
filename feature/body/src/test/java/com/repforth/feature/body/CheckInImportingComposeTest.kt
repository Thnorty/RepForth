package com.repforth.feature.body

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Pose
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** While a photo is still being added, the screen says so and Save waits. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class CheckInImportingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(importing: Set<Pose>) {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            RepForthPreviewHost {
                CheckInScreen(state = CheckInScreenshotTest.STATE.copy(importing = importing), cameraAvailable = true)
            }
        }
    }

    @Test
    fun `save waits while a photo is being added`() {
        show(importing = setOf(Pose.SIDE))

        compose.onNodeWithText("Adding photo…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `save is offered once nothing is being added`() {
        show(importing = emptySet())

        compose.onNodeWithText("Adding photo…").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsEnabled()
    }
}
