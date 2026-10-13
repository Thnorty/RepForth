package com.repforth.feature.body

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class TrackingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private var deleting: String? = null
    private var toggled: Pair<String, Boolean>? = null

    @Before
    fun setUp() {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            RepForthPreviewHost {
                TrackingScreen(
                    state = TrackingUiState(BodyFixtures.TYPES),
                    onToggle = { id, tracked -> toggled = id to tracked },
                    onAskDelete = { deleting = it },
                )
            }
        }
    }

    @Test
    fun `built-ins offer no delete`() {
        compose.onNodeWithText("Weight").assertExists()
        compose.onNodeWithContentDescription("Delete Weight").assertDoesNotExist()

        // Below the fold of a lazy list, so not composed until scrolled to.
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasContentDescription("Delete Abdominal skinfold"))
        compose.onNodeWithContentDescription("Delete Abdominal skinfold").performClick()

        assertEquals(BodyFixtures.SKINFOLD.id, deleting)
    }

    @Test
    fun `tapping a row flips its switch`() {
        compose.onNodeWithText("Neck").performScrollTo().performClick()

        assertEquals("neck" to true, toggled)
    }
}
