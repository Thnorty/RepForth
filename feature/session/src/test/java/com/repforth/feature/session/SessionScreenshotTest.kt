package com.repforth.feature.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_COMPARISON
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.screenshotPath
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
 * The running workout, in the three phases it spends its time in.
 *
 * The one screen where §12 sets a higher bar than the rest: controls tapped
 * mid-set are 64dp because the user is out of breath with chalk on their hands.
 * That target is exactly what a long exercise name at 200% font scale threatens,
 * and this is also where a Save button once sat behind the keyboard — enabled,
 * invisible, untappable — which was found by a person holding a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class SessionScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun session_active_english() = capture("session-active-en", active())

    @Test
    fun session_active_turkish() = capture("session-active-tr", active(), locale = TURKISH)

    @Test
    fun session_active_large_text() =
        capture("session-active-en-2x", active(), fontScale = 2f)

    @Test
    fun session_active_turkish_large_text() =
        capture("session-active-tr-2x", active(), locale = TURKISH, fontScale = 2f)

    @Test
    fun session_resting() = capture(
        "session-resting-en",
        // 48 of the planned 90 seconds left, so the ring is caught mid-sweep:
        // a full or empty ring would prove nothing about where it starts or
        // which way it goes.
        resting(),
    )

    /**
     * The ring is a fixed number of dp around text that is not.
     *
     * That is the whole risk of drawing one: at 200% the countdown numeral and
     * its label have to stay inside a circle that did not grow with them, and
     * Turkish's "Dinlenme" is twice the length of "Rest". Neither case had a
     * golden before the ring existed.
     */
    @Test
    fun session_resting_turkish() = capture(
        "session-resting-tr",
        resting(),
        locale = TURKISH,
    )

    @Test
    fun session_resting_large_text() = capture(
        "session-resting-en-2x",
        resting(),
        fontScale = 2f,
    )

    @Test
    fun session_resting_turkish_large_text() = capture(
        "session-resting-tr-2x",
        resting(),
        locale = TURKISH,
        fontScale = 2f,
    )

    @Test
    fun session_paused() = capture("session-paused-en", active(phase = SessionPhase.PAUSED))

    /** Nothing running is a real state: Session is reachable with no session. */
    @Test
    fun session_none() = capture("session-none-en", SessionUiState(loading = false))

    private fun capture(
        name: String,
        state: SessionUiState,
        locale: String? = null,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers("+${locale ?: ENGLISH}")
        RuntimeEnvironment.setFontScale(fontScale)

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

        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }

    /** 48 of the planned 90 seconds left, so the ring is caught mid-sweep. */
    private fun resting() =
        active(phase = SessionPhase.RESTING).copy(restRemainingMs = 48_000L)

    private fun active(phase: SessionPhase = SessionPhase.ACTIVE) = SessionUiState(
        snapshot = SessionSnapshot(
            sessionId = "s1",
            templateId = "t1",
            phase = phase,
            phaseBeforePause = if (phase == SessionPhase.PAUSED) SessionPhase.ACTIVE else null,
            exercises = EXERCISES,
            currentExerciseIndex = 0,
            currentSetIndex = 1,
            // Fixed, so a golden does not change with the clock.
            startedAt = JANUARY_2026,
        ),
        summaries = SUMMARIES,
        loading = false,
    )

    private companion object {
        /** A fixed instant, so a golden does not change with the clock. */
        const val JANUARY_2026 = 1_767_225_600_000L

        /**
         * The longest name in the catalog's usual shape, and a next-up beside it.
         *
         * A short name proves nothing about a screen whose whole job is showing
         * one large enough to read across a gym.
         */
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
