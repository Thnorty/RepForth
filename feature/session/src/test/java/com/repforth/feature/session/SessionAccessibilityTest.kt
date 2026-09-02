package com.repforth.feature.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.assertScreenIsAccessible
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
 * What TalkBack would find in a running workout.
 *
 * The screen with the most icon-only controls in the app, and the one where
 * §12 already demands 64dp targets — so the checks here are the ones with the
 * most to catch and the least excuse for failing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class SessionAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun active_english() = check(ENGLISH, active())

    @Test
    fun active_turkish() = check(TURKISH, active())

    @Test
    fun resting_english() = check(ENGLISH, active(SessionPhase.RESTING))

    @Test
    fun paused_english() = check(ENGLISH, active(SessionPhase.PAUSED))

    private fun check(locale: String, state: SessionUiState) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)

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
                )
            }
        }

        compose.assertScreenIsAccessible("Session ${state.snapshot?.phase} ($locale)")
    }

    private fun active(phase: SessionPhase = SessionPhase.ACTIVE) = SessionUiState(
        snapshot = SessionSnapshot(
            sessionId = "s1",
            templateId = "t1",
            phase = phase,
            phaseBeforePause = if (phase == SessionPhase.PAUSED) SessionPhase.ACTIVE else null,
            exercises = EXERCISES,
            currentExerciseIndex = 0,
            currentSetIndex = 1,
            startedAt = JANUARY_2026,
        ),
        summaries = SUMMARIES,
        loading = false,
    )

    private companion object {
        const val JANUARY_2026 = 1_767_225_600_000L

        val SUMMARIES = mapOf(
            "0025" to summary("0025", "barbell decline wide-grip press"),
            "0043" to summary("0043", "dumbbell incline hammer curl"),
        )

        val EXERCISES = listOf(
            SessionExercise(
                id = "e0",
                exerciseId = ExerciseId("0025"),
                position = 0,
                target = ExerciseTarget.Reps(sets = 4, reps = 12, weightKg = 60.0),
                restMs = 90_000L,
            ),
            SessionExercise(
                id = "e1",
                exerciseId = ExerciseId("0043"),
                position = 1,
                target = ExerciseTarget.Reps(sets = 3, reps = 10, weightKg = 14.0),
                restMs = 60_000L,
            ),
        )

        fun summary(id: String, name: String) = ExerciseSummary(
            id = ExerciseId(id),
            name = name,
            bodyPart = BodyPart.CHEST,
            target = Muscle.PECTORALS,
            equipment = Equipment.BARBELL,
            thumbnail = MediaRef.Unavailable,
        )
    }
}
