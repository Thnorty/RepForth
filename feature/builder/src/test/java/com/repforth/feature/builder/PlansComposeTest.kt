package com.repforth.feature.builder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.TrainingWeek
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Choosing which week Today follows.
 *
 * Written because that could not be done at all. `onSetActive` was threaded
 * from `WeekDao.setActive` through the repository, the view model and
 * `PlansScreen`'s parameter list, and then never called by the card that
 * received it — so `active` was whatever `BuilderViewModel` set when the week
 * was saved, which is `true` for the first week and `false` for every one
 * after. A second week could be built and never became the active one.
 *
 * Nothing could have reported that. An unused lambda parameter is legal, the
 * parameter has a default so no call site had to supply it, and the goldens
 * cannot see it either: an inactive week's card starts collapsed, so the
 * control is not in the picture that would have changed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class PlansComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an inactive week can be made the active one`() {
        var madeActive: String? = null
        render(BuilderFixtures.WEEK.copy(active = false)) { madeActive = it }

        // Expanded first, which is how a person reaches it: the control is in
        // the body of the card, and an inactive week starts collapsed.
        compose.onNodeWithText(BuilderFixtures.WEEK.name).performClick()
        compose.onNodeWithText("Set as active").performClick()

        assertEquals(BuilderFixtures.WEEK.id, madeActive)
    }

    /**
     * The active week offers no way to become active again.
     *
     * Not tidiness: `setActive` clears every other week's flag and sets this
     * one, so tapping it on the week that already holds it is a write that
     * changes nothing, and a control that does nothing is worse than no
     * control. The badge says so instead.
     */
    @Test
    fun `the active week shows the badge and no control`() {
        render(BuilderFixtures.WEEK.copy(active = true))

        compose.onNodeWithText("Active").assertIsDisplayed()
        assertTrue(
            "The active week must not offer to be made active",
            compose.onAllNodesWithText("Set as active").fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun render(week: TrainingWeek, onSetActive: (String) -> Unit = {}) {
        compose.setContent {
            RepForthPreviewHost {
                PlansScreen(
                    plans = emptyList(),
                    weeklyPlans = listOf(week),
                    onNewWorkout = {},
                    onEditPlan = {},
                    onEditWeek = {},
                    onStartPlan = {},
                    onDelete = {},
                    onSetActiveWeek = onSetActive,
                )
            }
        }
    }
}
