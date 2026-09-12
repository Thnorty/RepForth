package com.repforth.feature.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §3's effort and note, through the real screen.
 *
 * **Both are asked once, at the end**, and the first version of this got that
 * wrong: effort sat beside the reps and weight fields, so it was asked on every
 * set. Eight or more times a session, for an answer that mostly would not vary —
 * which is how a question stops being answered honestly, and which put it
 * directly in the way of §12's one-tap logging.
 *
 * So the assertions that matter are about *where* the questions are, not only
 * that they work. `the logging path is untouched` is the one that would have
 * caught the first version.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class EffortAndNoteComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private var logged = 0
    private var finishedWith: String? = null
    private var finishedEffort: Int? = null
    private var finishes = 0

    // ---- Neither question is on the logging path ----

    /**
     * The one that would have caught the first version of this.
     *
     * Effort was beside the reps and weight fields, asked on every set. Logging
     * is one tap and stays one tap, and neither question appears until there is
     * nothing left to do.
     */
    @Test
    fun `the logging path is untouched`() {
        render()

        assertEquals(
            "Effort is a question about the workout, not about each set",
            0,
            compose.onAllNodesWithText(EFFORT).fetchSemanticsNodes().size,
        )
        assertEquals(0, compose.onAllNodesWithText(NOTE_LABEL).fetchSemanticsNodes().size)

        compose.onNodeWithText(LOG).performClick()
        assertEquals("One tap, and nothing asked", 1, logged)
    }

    // ---- Effort, at the end ----

    /**
     * Five sentences, in order, and no numbers.
     *
     * A number out of ten asks the user to invent a scale before they can
     * answer; these have an obviously right answer. The order matters as much as
     * the wording — this is a scale, and "Just right" sits in the middle of it.
     */
    @Test
    fun `the five answers are offered when the workout is over`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(EFFORT).assertIsDisplayed()
        EFFORT_ANSWERS.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `a chosen answer reaches finish as its position on the scale`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(JUST_RIGHT).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertEquals("The middle of five", 3, finishedEffort)
    }

    /**
     * The ends of the scale, so the mapping cannot be off by one.
     *
     * Two tests rather than one: a `ComposeTestRule` hosts a single
     * `setContent`, so a test that rendered twice would fail for a reason that
     * has nothing to do with the scale.
     */
    @Test
    fun `the easiest answer is one`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(EFFORT_ANSWERS.first()).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertEquals(1, finishedEffort)
    }

    @Test
    fun `the hardest answer is five`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(EFFORT_ANSWERS.last()).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertEquals(5, finishedEffort)
    }

    /**
     * An optional control that cannot be un-answered is a required one.
     *
     * Tapping the same number again clears it, which is the only way back to
     * "did not say" once something has been touched.
     */
    @Test
    fun `tapping the same effort again clears it`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(JUST_RIGHT).performClick()
        compose.onNodeWithText(JUST_RIGHT).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertNull(finishedEffort)
    }

    @Test
    fun `choosing a different answer replaces the first`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(EFFORT_ANSWERS.first()).performClick()
        compose.onNodeWithText(JUST_RIGHT).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertEquals(3, finishedEffort)
    }

    // ---- The note, beside it ----

    @Test
    fun `the note is offered when the workout is over`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(NOTE_LABEL).assertIsDisplayed()
    }

    @Test
    fun `what was written reaches finish`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(NOTE_LABEL).performTextInput(NOTE)
        compose.onNodeWithText(FINISH).performClick()

        assertEquals(NOTE, finishedWith)
    }

    /**
     * Finishing without answering either still finishes.
     *
     * Both are optional, and the engine turns blank into nothing — but the
     * button must not become conditional on them.
     */
    @Test
    fun `finishing without answering anything still finishes`() {
        render(phase = SessionPhase.COMPLETING)

        compose.onNodeWithText(FINISH).performClick()

        assertEquals(1, finishes)
        assertEquals("", finishedWith)
        assertNull(finishedEffort)
    }

    private fun render(
        phase: SessionPhase = SessionPhase.ACTIVE,
        timed: Boolean = false,
    ) {
        val target = if (timed) {
            ExerciseTarget.Duration(sets = 3, durationMs = 60_000L)
        } else {
            // Not 10: the target panel draws the rep count as its hero number,
            // and a fixture of 10 makes "the whole scale is offered" match two
            // nodes -- the chip and the prescription.
            ExerciseTarget.Reps(sets = 3, reps = 12)
        }
        compose.setContent {
            RepForthPreviewHost {
                SessionScreen(
                    state = SessionUiState(snapshot = snapshot(phase, target), loading = false),
                    onCompleteSet = { _, _, _ -> logged++ },
                    onSkipSet = {},
                    onSkipRest = {},
                    onPause = {},
                    onResume = {},
                    onFinish = { note, effort, _ ->
                        finishes++
                        finishedWith = note
                        finishedEffort = effort
                    },
                    onAbandon = {},
                    onKeepRunningSession = {},
                    onDiscardRunningAndStart = {},
                )
            }
        }
    }

    private fun snapshot(phase: SessionPhase, target: ExerciseTarget) = SessionSnapshot(
        sessionId = "s1",
        templateId = "core-day",
        phase = phase,
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

    private companion object {
        const val LOG = "Log set"
        const val FINISH = "Finish workout"
        const val EFFORT = "How hard was it? (optional)"
        const val JUST_RIGHT = "Just right"
        val EFFORT_ANSWERS = listOf(
            "Too easy",
            "A little easy",
            JUST_RIGHT,
            "A little hard",
            "Too hard",
        )
        const val NOTE_LABEL = "Workout note (optional)"
        const val NOTE = "Shoulder felt off on the last set"
    }
}
