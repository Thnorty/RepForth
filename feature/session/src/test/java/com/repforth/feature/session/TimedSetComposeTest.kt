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
 * What a running timed set offers, through the real screen.
 *
 * The engine's rules have their own tests in `core:workout`, and those are not
 * enough on their own: the question that catches the interesting failure is what
 * calls this, and under what state does that caller reach it. A screen that
 * still drew "Log set" for a timed exercise would send a command the engine now
 * refuses, and the user would tap a button that does nothing — with every engine
 * test still green.
 *
 * The countdown itself is the other half. It replaces the target as the big
 * number, so a screen that kept showing the prescription would look correct in a
 * screenshot and never move.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class TimedSetComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val logged = mutableListOf<Triple<Int?, Double?, Long?>>()
    private var skips = 0

    @Test
    fun `a running timed set counts down instead of showing the target`() {
        render(setRemainingMs = 42_000L)

        compose.onNodeWithText("42").assertIsDisplayed()
        assertEquals(
            "The prescription is what the seconds started at, not what is drawn",
            0,
            compose.onAllNodesWithText("60").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `there is no way to log a timed set by hand`() {
        render(setRemainingMs = 42_000L)

        assertEquals(
            "The engine refuses a CompleteSet for timed work, so the button must not be offered",
            0,
            compose.onAllNodesWithText("Log set").fetchSemanticsNodes().size,
        )
    }

    /** Stopping early is still possible, and is still a skip. */
    @Test
    fun `skip set is still offered while a timed set runs`() {
        render(setRemainingMs = 42_000L)

        compose.onNodeWithText("Skip set").performClick()

        assertEquals(1, skips)
        assertEquals("Skipping is not logging", 0, logged.size)
    }

    @Test
    fun `pause is still offered while a timed set runs`() {
        render(setRemainingMs = 42_000L)

        compose.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun `an untimed set still logs by hand`() {
        // The pair. Hiding the button for every exercise would pass every test
        // above and break ordinary logging entirely.
        render(setRemainingMs = null, timed = false)

        compose.onNodeWithText("Log set").performClick()

        assertEquals(1, logged.size)
    }

    @Test
    fun `a timed set with no clock yet shows its target`() {
        // Before the first tick arrives the remainder is null, and the screen has
        // to draw something. The prescription is the honest answer.
        render(setRemainingMs = null)

        compose.onNodeWithText("60").assertIsDisplayed()
    }

    private fun render(setRemainingMs: Long?, timed: Boolean = true) {
        val target = if (timed) {
            ExerciseTarget.Duration(sets = 3, durationMs = 60_000L)
        } else {
            ExerciseTarget.Reps(sets = 3, reps = 10)
        }
        compose.setContent {
            RepForthPreviewHost {
                SessionScreen(
                    state = SessionUiState(
                        snapshot = snapshot(target),
                        setRemainingMs = setRemainingMs,
                        loading = false,
                    ),
                    onCompleteSet = { reps, weight, duration ->
                        logged += Triple(reps, weight, duration)
                    },
                    onSkipSet = { skips++ },
                    onSkipRest = {},
                    onPause = {},
                    onResume = {},
                    onFinish = { _, _, _ -> },
                    onAbandon = {},
                    onKeepRunningSession = {},
                    onDiscardRunningAndStart = {},
                )
            }
        }
    }

    private fun snapshot(target: ExerciseTarget) = SessionSnapshot(
        sessionId = "s1",
        templateId = "core-day",
        phase = SessionPhase.ACTIVE,
        phaseBeforePause = null,
        exercises = listOf(
            SessionExercise(
                id = "e0",
                exerciseId = ExerciseId("ex-0"),
                position = 0,
                target = target,
                restMs = 30_000L,
            ),
        ),
        currentExerciseIndex = 0,
        currentSetIndex = 0,
        startedAt = 1_767_225_600_000L,
    )
}
