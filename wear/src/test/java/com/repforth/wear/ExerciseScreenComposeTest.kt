package com.repforth.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.WATCH_SCREENSHOT_DEVICE
import com.repforth.core.wearprotocol.WearAction
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the watch offers for a set, through the real screen.
 *
 * **The first behavioural test the watch module has had.** Everything under
 * `wear/src/test` was string parity, and the protocol and projection tests live
 * in shared modules — which prove the wire format and prove nothing about what a
 * wrist actually shows or sends.
 *
 * The gap that closes is not hypothetical. `SessionEngine` began refusing
 * `CompleteSet` for a timed exercise when the clock became the only way to end
 * one, and the watch went on drawing a Complete button for every set. Every
 * engine test stayed green, because they test the rule and the watch is a
 * *second sender* of the command the rule refuses. Nothing in the suite could
 * see a button that did nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = WATCH_SCREENSHOT_DEVICE)
class ExerciseScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<WearAction>()

    @Test
    fun `a timed set offers no way to complete it by hand`() {
        render(timed = true, remainingSeconds = 42)

        assertEquals(
            "The phone refuses a CompleteSet for timed work, so the wrist must not ask",
            0,
            compose.onAllNodesWithText(COMPLETE).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `a timed set counts down instead of showing the set number as the hero`() {
        render(timed = true, remainingSeconds = 42)

        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText(SECONDS).assertIsDisplayed()
    }

    /**
     * Before the phone arms a clock there is nothing to count, and the screen
     * still has to draw something. The prescription is the honest answer.
     */
    @Test
    fun `a timed set with no clock yet shows how long it is`() {
        render(timed = true, remainingSeconds = null)

        compose.onNodeWithText("60").assertIsDisplayed()
    }

    /** Stopping early is still allowed, and is still a skip. */
    @Test
    fun `skip is still offered while a timed set runs`() {
        render(timed = true, remainingSeconds = 42)

        compose.onNodeWithText(SKIP).performClick()

        assertEquals(listOf(WearAction.SkipSet), sent)
    }

    @Test
    fun `pause is still offered while a timed set runs`() {
        render(timed = true, remainingSeconds = 42)

        compose.onNodeWithText(PAUSE).performClick()

        assertEquals(listOf(WearAction.Pause), sent)
    }

    /**
     * The pair. Hiding Complete for every exercise would pass every assertion
     * above and break the button the whole app exists for.
     */
    @Test
    fun `a set counted in reps still completes by hand`() {
        render(timed = false, remainingSeconds = null)

        compose.onNodeWithText(REPS).assertIsDisplayed()
        compose.onNodeWithText(COMPLETE).performClick()

        assertEquals(listOf(WearAction.CompleteSet), sent)
    }

    /**
     * §11: "all modifying actions are disabled and clearly marked unavailable"
     * when the phone cannot be reached. A timed set has fewer buttons, so this
     * asserts the remaining ones still honour it.
     */
    @Test
    fun `a disconnected watch cannot skip a timed set`() {
        render(timed = true, remainingSeconds = 42, enabled = false)

        compose.onNodeWithText(SKIP).performClick()

        assertEquals(emptyList<WearAction>(), sent)
    }

    private fun render(timed: Boolean, remainingSeconds: Int?, enabled: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                ExerciseScreen(
                    state = state(timed),
                    remainingSeconds = remainingSeconds,
                    enabled = enabled,
                    onAction = { sent += it },
                )
            }
        }
    }

    private fun state(timed: Boolean) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = WearPhase.Exercise,
        exerciseId = "0025",
        exerciseName = "front plank",
        setNumber = 1,
        totalSets = 3,
        targetReps = if (timed) null else 12,
        targetDurationMs = if (timed) 60_000L else null,
        restDeadlineElapsedRealtimeMs = null,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = null,
    )

    private companion object {
        const val COMPLETE = "Complete"
        const val SKIP = "Skip"
        const val PAUSE = "Pause"
        const val SECONDS = "Seconds"
        const val REPS = "12 reps"
    }
}
