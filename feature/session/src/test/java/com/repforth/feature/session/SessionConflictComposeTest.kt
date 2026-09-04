package com.repforth.feature.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The question the screen asks when a different workout is already running.
 *
 * Written after the dialog shipped and was never seen. Two separate mistakes
 * kept it off screen, and neither was visible in a unit test of the state
 * machine underneath: the route never asked `start` for an outcome at all, and
 * the dialog was rendered after an early `return` that fires whenever there is
 * no snapshot yet. The second is what this covers — the first is a three-line
 * effect in `SessionRoute` and was verified on a device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class SessionConflictComposeTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The trap, as an assertion.
     *
     * `start` answers `Blocked` as soon as it is asked, which can be before the
     * running session's snapshot has been adopted into the state. While the
     * dialog sat below `if (snapshot == null) return`, that window rendered
     * "no workout" and no question — leaving the user on an empty screen with
     * the plan they tapped simply ignored.
     */
    @Test
    fun `the conflict is asked even before the running session has been adopted`() {
        render(SessionUiState(snapshot = null, conflictingSession = RUNNING, loading = false))

        compose.onNodeWithText("A workout is already running").assertIsDisplayed()
    }

    @Test
    fun `the conflict is asked over a running session too`() {
        render(
            SessionUiState(
                snapshot = RUNNING,
                conflictingSession = RUNNING,
                loading = false,
            ),
        )

        compose.onNodeWithText("A workout is already running").assertIsDisplayed()
    }

    /** Both answers are offered, and neither is taken on the user's behalf. */
    @Test
    fun `keeping and discarding are both offered and neither happens on its own`() {
        var kept = 0
        var discarded = 0
        render(
            state = SessionUiState(snapshot = null, conflictingSession = RUNNING, loading = false),
            onKeep = { kept++ },
            onDiscard = { discarded++ },
        )

        assertEquals("Nothing may be decided without a tap", 0, kept + discarded)

        compose.onNodeWithText("Go back to it").performClick()
        assertEquals(1, kept)
        assertEquals(0, discarded)
    }

    @Test
    fun `discarding is the other answer`() {
        var discarded = 0
        render(
            state = SessionUiState(snapshot = null, conflictingSession = RUNNING, loading = false),
            onDiscard = { discarded++ },
        )

        compose.onNodeWithText("Discard and start").performClick()

        assertEquals(1, discarded)
    }

    /** No conflict, no dialog — the screen is not permanently asking. */
    @Test
    fun `an ordinary workout is not interrupted by the question`() {
        render(SessionUiState(snapshot = RUNNING, loading = false))

        assertEquals(
            0,
            compose.onAllNodesWithText("A workout is already running").fetchSemanticsNodes().size,
        )
    }

    private fun render(
        state: SessionUiState,
        onKeep: () -> Unit = {},
        onDiscard: () -> Unit = {},
    ) {
        compose.setContent {
            RepForthPreviewHost {
                SessionScreen(
                    state = state,
                    onCompleteSet = { _, _, _ -> },
                    onSkipSet = {},
                    onSkipRest = {},
                    onNextExercise = {},
                    onPause = {},
                    onResume = {},
                    onFinish = {},
                    onAbandon = {},
                    onKeepRunningSession = onKeep,
                    onDiscardRunningAndStart = onDiscard,
                )
            }
        }
    }

    private companion object {
        val RUNNING = SessionSnapshot(
            sessionId = "s1",
            templateId = "push-day",
            phase = SessionPhase.ACTIVE,
            phaseBeforePause = null,
            exercises = listOf(
                SessionExercise(
                    id = "e0",
                    exerciseId = ExerciseId("ex-0"),
                    position = 0,
                    target = ExerciseTarget.Reps(sets = 3, reps = 10),
                    restMs = 60_000L,
                ),
            ),
            currentExerciseIndex = 0,
            currentSetIndex = 0,
            startedAt = 1_767_225_600_000L,
        )
    }
}
