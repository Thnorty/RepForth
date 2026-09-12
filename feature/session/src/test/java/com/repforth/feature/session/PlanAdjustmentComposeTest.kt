package com.repforth.feature.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §3's answer carried into the plan, through the real screen.
 *
 * The rule itself is proved in `core:model`'s `ProgressionTest`. What is proved
 * here is the half a unit test cannot reach: **that the offer is only ever made
 * when it should be, and only ever accepted on purpose.** It writes to a plan
 * the user keeps, so every one of these is about a way it could write when
 * nobody asked it to.
 *
 * The shape AGENTS.md warns about is exactly the risk: a green test over a
 * function nothing calls. `SessionViewModel.onFinish` takes the acceptance as an
 * argument, and if the screen never passed `true` the whole feature would be
 * dead with every rule test still passing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class PlanAdjustmentComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private var adjusted: Boolean? = null
    private var finishedEffort: Int? = null

    // ------------------------------------------------- when it is not offered

    /** Nothing is offered until the question has been answered. */
    @Test
    fun `no offer before an answer`() {
        render()

        assertEquals(0, compose.onAllNodesWithText(HARDER).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText(EASIER).fetchSemanticsNodes().size)
    }

    /**
     * **"Just right" offers nothing, and that is the point of a five-point
     * scale.** The middle is where a well-judged session lands; there is nothing
     * to correct, so there is nothing to ask.
     */
    @Test
    fun `just right offers nothing`() {
        render()

        compose.onNodeWithText(JUST_RIGHT).performClick()

        assertEquals(0, compose.onAllNodesWithText(HARDER).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText(EASIER).fetchSemanticsNodes().size)
    }

    /** A workout with no plan behind it has nowhere to write. */
    @Test
    fun `a session from no plan offers nothing`() {
        render(plan = null)

        compose.onNodeWithText(A_LITTLE_EASY).performClick()

        assertEquals(0, compose.onAllNodesWithText(HARDER).fetchSemanticsNodes().size)
    }

    // ----------------------------------------------------- what it then says

    @Test
    fun `an easy workout offers to make the plan harder`() {
        render()

        compose.onNodeWithText(A_LITTLE_EASY).performClick()

        compose.onNodeWithText(HARDER).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText(EASIER).fetchSemanticsNodes().size)
    }

    @Test
    fun `a hard workout offers the other direction`() {
        render()

        compose.onNodeWithText(TOO_HARD).performClick()

        compose.onNodeWithText(EASIER).assertIsDisplayed()
    }

    /** The receipt names the exercise and both numbers, so nothing is a surprise. */
    @Test
    fun `the card says what the plan will read`() {
        render()

        compose.onNodeWithText(A_LITTLE_EASY).performClick()

        // Twice: the screen's own header names the exercise just finished, and
        // the receipt names it again as a row. Asserting "displayed" alone finds
        // two nodes and fails, which is worth writing down rather than working
        // around -- the header is the reason the receipt has to carry a number
        // as well as a name.
        assertEquals(2, compose.onAllNodesWithText(BENCH).fetchSemanticsNodes().size)
        compose.onNodeWithText("60 → 65 kg").assertIsDisplayed()
    }

    /**
     * **A light load moves underneath without the plan reading differently**,
     * and the card has to say so rather than drawing "10 kg → 10 kg", which
     * looks broken rather than patient.
     */
    @Test
    fun `an exercise that only creeps is described rather than listed`() {
        render()

        compose.onNodeWithText(A_LITTLE_EASY).performClick()

        assertEquals(
            "A row whose numbers are identical must not be drawn",
            0,
            compose.onAllNodesWithText("10 → 10 kg").fetchSemanticsNodes().size,
        )
        compose.onNodeWithText(CREEPING, substring = true).assertIsDisplayed()
    }

    // --------------------------------------------------- accepting it, or not

    /**
     * **The default is the one where Finish changes nothing but this workout.**
     * The offer edits something the user keeps, so silence is a no.
     */
    @Test
    fun `finishing without touching the card writes nothing`() {
        render()

        compose.onNodeWithText(A_LITTLE_EASY).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertFalse("An untouched offer is a refused one", adjusted!!)
        assertEquals(2, finishedEffort)
    }

    /** And the tap is what accepts it. This is the one that proves it is reached. */
    @Test
    fun `tapping the card carries the acceptance to finish`() {
        render()

        compose.onNodeWithText(A_LITTLE_EASY).performClick()
        compose.onNodeWithText(HARDER).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertTrue(adjusted!!)
    }

    /** Tapping it twice is a change of mind, not a double acceptance. */
    @Test
    fun `tapping the card again refuses it`() {
        render()

        compose.onNodeWithText(A_LITTLE_EASY).performClick()
        compose.onNodeWithText(HARDER).performClick()
        compose.onNodeWithText(HARDER).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertFalse(adjusted!!)
    }

    /**
     * **Changing the answer withdraws the acceptance.**
     *
     * Accepting "harder", then deciding it was actually too hard, must not leave
     * a plan about to be made harder. The acceptance was of a particular set of
     * numbers, and a different answer is a different set.
     */
    @Test
    fun `changing the answer withdraws an acceptance`() {
        render()

        compose.onNodeWithText(A_LITTLE_EASY).performClick()
        compose.onNodeWithText(HARDER).performClick()
        compose.onNodeWithText(TOO_HARD).performClick()
        compose.onNodeWithText(FINISH).performClick()

        assertFalse("The card now says the opposite of what was accepted", adjusted!!)
        assertEquals(5, finishedEffort)
    }

    private fun render(plan: WorkoutTemplate? = template()) {
        compose.setContent {
            RepForthPreviewHost {
                SessionScreen(
                    state = SessionUiState(
                        snapshot = snapshot(),
                        plan = plan,
                        summaries = SUMMARIES,
                        loading = false,
                    ),
                    onCompleteSet = { _, _, _ -> },
                    onSkipSet = {},
                    onSkipRest = {},
                    onPause = {},
                    onResume = {},
                    onFinish = { _, effort, adjust ->
                        finishedEffort = effort
                        adjusted = adjust
                    },
                    onAbandon = {},
                    onKeepRunningSession = {},
                    onDiscardRunningAndStart = {},
                )
            }
        }
    }

    private fun snapshot() = SessionSnapshot(
        sessionId = "s1",
        templateId = PLAN_ID,
        phase = SessionPhase.COMPLETING,
        exercises = listOf(
            SessionExercise(
                id = "se1",
                exerciseId = ExerciseId(BENCH_ID),
                position = 0,
                target = ExerciseTarget.Reps(sets = 4, reps = 8, weightKg = 60.0),
                restMs = 90_000L,
            ),
        ),
        currentExerciseIndex = 0,
        currentSetIndex = 3,
        startedAt = 1_767_225_600_000L,
    )

    private fun template() = WorkoutTemplate(
        id = PLAN_ID,
        name = PLAN_NAME,
        source = PlanSource.MANUAL,
        exercises = listOf(
            // Heavy enough that the ceiling binds, so it moves on the plan.
            planned(BENCH_ID, 0, ExerciseTarget.Reps(sets = 4, reps = 8, weightKg = 60.0)),
            // Light enough that a level is a tenth, so it only creeps.
            planned(RAISE_ID, 1, ExerciseTarget.Reps(sets = 3, reps = 12, weightKg = 10.0)),
        ),
    )

    private fun planned(id: String, position: Int, target: ExerciseTarget) = PlannedExercise(
        id = "pe$position",
        exerciseId = ExerciseId(id),
        position = position,
        target = target,
        restMs = 90_000L,
    )

    private companion object {
        const val PLAN_ID = "push-day"
        const val PLAN_NAME = "Push Day"
        const val BENCH_ID = "0025"
        const val RAISE_ID = "0043"
        const val BENCH = "Barbell Bench Press"
        const val RAISE = "Dumbbell Lateral Raise"

        const val HARDER = "Make Push Day harder next time"
        const val EASIER = "Make Push Day easier next time"
        const val CREEPING = "stays at 10 kg and moves closer to 15 kg"

        const val A_LITTLE_EASY = "A little easy"
        const val JUST_RIGHT = "Just right"
        const val TOO_HARD = "Too hard"
        const val FINISH = "Finish workout"

        val SUMMARIES = mapOf(
            BENCH_ID to summary(BENCH_ID, BENCH),
            RAISE_ID to summary(RAISE_ID, RAISE),
        )

        fun summary(id: String, name: String) = ExerciseSummary(
            id = ExerciseId(id),
            name = name,
            bodyPart = BodyPart.CHEST,
            target = Muscle.PECTORALS,
            equipment = Equipment.BARBELL,
            thumbnail = MediaRef.Unavailable,
            animation = MediaRef.Unavailable,
        )
    }
}
