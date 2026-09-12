package com.repforth.feature.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the rest screen says about the set you are about to do.
 *
 * It used to say the name and count the sets, beside a 48dp still — and rest is
 * the one moment in a workout with time to look at something. The preview is
 * now the exercise moving, at a size worth looking at, with the prescription
 * under it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class RestPreviewComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the next set's repetitions and weight are shown`() {
        render(ExerciseTarget.Reps(sets = 3, reps = 12, weightKg = 62.5))

        compose.onNodeWithText("12 reps · 62.5 kg").assertIsDisplayed()
    }

    /** Bodyweight is a load too, and saying nothing would read as unknown. */
    @Test
    fun `a set with no weight says bodyweight rather than nothing`() {
        render(ExerciseTarget.Reps(sets = 3, reps = 12))

        compose.onNodeWithText("12 reps · Bodyweight").assertIsDisplayed()
    }

    /**
     * A timed set shows what it prescribes, not a countdown.
     *
     * Nothing is running during a rest, so there is no remainder — the active
     * panel counts down because its clock is going, and this one cannot.
     */
    @Test
    fun `a timed set shows its prescription`() {
        render(ExerciseTarget.Duration(sets = 3, durationMs = 45_000L))

        compose.onNodeWithText("45 seconds · Bodyweight").assertIsDisplayed()
    }

    /**
     * **All of it survives 200% text, by scrolling rather than by fitting.**
     *
     * It did not. The panel sits between a fixed header and fixed controls, and
     * that column neither scrolled nor clipped visibly — it centred, so growing
     * the preview pushed the name, the set and the target off *both* ends and
     * the screen looked deliberate. §13 requires text to survive this scale, and
     * the target line is precisely what the redesign was asked for.
     *
     * `performScrollTo` throws when there is no scrollable ancestor, so removing
     * the scroll makes this red rather than merely weaker.
     */
    @Test
    fun `the target survives 200 percent font scale`() {
        render(ExerciseTarget.Reps(sets = 3, reps = 12, weightKg = 62.5), fontScale = 2f)

        compose.onNodeWithText("12 reps · 62.5 kg").performScrollTo().assertIsDisplayed()
    }

    private fun render(target: ExerciseTarget, fontScale: Float = 1f) {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent {
            RepForthPreviewHost {
                SessionScreen(
                    state = SessionUiState(
                        snapshot = snapshot(target),
                        restRemainingMs = 30_000L,
                        loading = false,
                    ),
                    onCompleteSet = { _, _, _ -> },
                    onSkipSet = {},
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

    /** Resting, with a second set of the same exercise still to come. */
    private fun snapshot(target: ExerciseTarget) = SessionSnapshot(
        sessionId = "s1",
        templateId = "core-day",
        phase = SessionPhase.RESTING,
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
