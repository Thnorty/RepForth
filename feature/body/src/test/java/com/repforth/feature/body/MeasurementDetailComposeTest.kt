package com.repforth.feature.body

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class MeasurementDetailComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private var opened: String? = null

    @Before
    fun setUp() {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(1f)
        compose.setContent {
            RepForthPreviewHost {
                MeasurementDetailScreen(state = MeasurementDetailFixtures.WAIST, onOpenCheckIn = { opened = it })
            }
        }
    }

    @Test
    fun `tapping an entry opens its check-in`() {
        compose.onNodeWithText("Oct 3, 2026", substring = true).performScrollTo().performClick()

        assertEquals("w1", opened)
    }

    /**
     * The chart is one sentence to TalkBack, not a scatter of axis labels.
     * Moved here from core:designsystem, which has no compose test setup.
     */
    @Test
    fun `the chart announces one sentence`() {
        val sentence = "Waist, from 91 cm to 88 cm over the last 3 months"
        val nodes = compose.onAllNodesWithContentDescription(sentence).fetchSemanticsNodes()

        assertEquals(1, nodes.size)
        assertEquals(
            "The chart's own text must not reach TalkBack separately",
            false,
            nodes.single().config.contains(SemanticsProperties.Text),
        )
    }
}
