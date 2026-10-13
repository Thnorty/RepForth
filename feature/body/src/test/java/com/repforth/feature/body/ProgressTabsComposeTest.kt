package com.repforth.feature.body

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The switch at the top of Progress reaches the Body tab, and back. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class ProgressTabsComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun locale() {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
    }

    @Test
    fun `workouts first, body on tap, workouts again on tap`() {
        compose.setContent {
            RepForthPreviewHost {
                ProgressTabs(workouts = { Text("workout history") }, body = { Text("body content") })
            }
        }
        compose.onNodeWithText("workout history").assertIsDisplayed()

        compose.onNodeWithText("Body").performClick()
        compose.onNodeWithText("body content").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("workout history").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("Workouts").performClick()
        compose.onNodeWithText("workout history").assertIsDisplayed()
    }
}
