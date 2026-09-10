package com.repforth.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.WATCH_SCREENSHOT_DEVICE
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §11's rest screen: the countdown, what comes next, and the two ways past it.
 *
 * The rest is where someone actually decides what to do next — it is the only
 * screen that names the exercise coming up — so it carries both exits: end the
 * rest and do the next set, or leave the exercise entirely. The phone offers
 * `NextExercise` under `isActive || isResting`, and this is the second half of
 * that condition.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = WATCH_SCREENSHOT_DEVICE)
class RestScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<WearAction>()

    @Test
    fun `the countdown and what comes next are both on screen`() {
        render(remainingSeconds = 45)

        compose.onNodeWithText("45").assertIsDisplayed()
        compose.onNodeWithText("Next: Barbell Squat").assertIsDisplayed()
    }

    /**
     * A rest with no number is drawn as an em dash rather than a zero.
     *
     * Zero would be a claim — that the rest is over — and the phone would then
     * disagree with the wrist. The dash says only that the watch does not know,
     * which is the truth while a snapshot is in flight.
     */
    @Test
    fun `a rest with no remainder shows a dash rather than a zero`() {
        render(remainingSeconds = null)

        compose.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun `skip rest is sent`() {
        render(remainingSeconds = 45)

        compose.onNodeWithText(SKIP_REST).performClick()

        assertEquals(listOf(WearAction.SkipRest), sent)
    }

    /** The half of `isActive || isResting` that the exercise screen does not cover. */
    @Test
    fun `next exercise can be sent while resting`() {
        render(remainingSeconds = 45)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.NextExercise), sent)
    }

    /** §11: disconnected means read-only, and that includes the new control. */
    @Test
    fun `a disconnected watch can do neither`() {
        render(remainingSeconds = 45, enabled = false)

        compose.onNodeWithText(SKIP_REST).performScrollTo().performClick()
        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(emptyList<WearAction>(), sent)
    }

    /**
     * A paused rest offers the way out of the pause, not a skip.
     *
     * The engine refuses a `SkipRest` during a pause, so the button that was
     * here would have done nothing — and resuming is the only way forward. This
     * is the screen half of the paused-rest defect found on hardware; the other
     * half was that the wrist never reached this screen at all.
     */
    @Test
    fun `a paused rest offers resume rather than skip`() {
        render(remainingSeconds = 25, paused = true)

        assertEquals(0, compose.onAllNodesWithText(SKIP_REST).fetchSemanticsNodes().size)
        compose.onNodeWithText(RESUME).performClick()

        assertEquals(listOf(WearAction.Resume), sent)
    }

    /** And the countdown is still shown, frozen at what is owed. */
    @Test
    fun `a paused rest still shows what it owes`() {
        render(remainingSeconds = 25, paused = true)

        compose.onNodeWithText("25").assertIsDisplayed()
    }

    @Test
    fun `every control is still reachable at 200 percent font scale`() {
        render(remainingSeconds = 45, fontScale = 2f)

        compose.onNodeWithText(SKIP_REST).performScrollTo().performClick()
        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.SkipRest, WearAction.NextExercise), sent)
    }

    private fun render(
        remainingSeconds: Int?,
        enabled: Boolean = true,
        fontScale: Float = 1f,
        paused: Boolean = false,
    ) {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent {
            MaterialTheme {
                RestScreen(
                    state = state(paused),
                    remainingSeconds = remainingSeconds,
                    enabled = enabled,
                    onAction = { sent += it },
                )
            }
        }
    }

    private fun state(paused: Boolean = false) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = if (paused) WearPhase.Paused else WearPhase.Rest,
        exerciseId = "0025",
        exerciseName = "front plank",
        setNumber = 1,
        totalSets = 3,
        targetReps = 12,
        targetDurationMs = null,
        restDeadlineElapsedRealtimeMs = null,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = "Barbell Squat",
    )

    private companion object {
        const val SKIP_REST = "Skip rest"
        const val RESUME = "Resume"
        const val NEXT = "Next exercise"
    }
}
