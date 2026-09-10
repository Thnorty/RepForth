package com.repforth.feature.session

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.UnitSystem
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the weight field actually records, through the real screen.
 *
 * The parser has its own tests in `core:designsystem`, and they are not enough
 * on their own — the question that catches the interesting failure is what calls
 * this, and under what state does that caller reach it. This is that caller: it
 * hosts `SessionScreen`, types into the field the way a person does, and reads
 * the kilograms handed to `onCompleteSet`.
 *
 * The defect it was written for is a silent one. `12,5` was filtered down to
 * `125` and logged, and anything unreadable became null, which this screen
 * already uses to mean "as prescribed" — so a mistyped weight recorded the
 * planned number and said nothing at all.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class SessionWeightEntryComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val logged = mutableListOf<Triple<Int?, Double?, Long?>>()
    private var unitLabel = "kg"

    @Test
    fun `a comma is a decimal point, not a digit to drop`() {
        render()

        typeWeight("12,5")
        logSet()

        assertEquals(12.5, logged.single().second!!, 0.0001)
    }

    @Test
    fun `a period means the same thing`() {
        render()

        typeWeight("12.5")
        logSet()

        assertEquals(12.5, logged.single().second!!, 0.0001)
    }

    @Test
    fun `blank still means as prescribed`() {
        render()

        logSet()

        assertNull(
            "Nothing typed must stay 'as planned', which is what the engine fills in",
            logged.single().second,
        )
    }

    @Test
    fun `zero is recorded as zero and not as the planned weight`() {
        render()

        typeWeight("0")
        logSet()

        assertEquals(0.0, logged.single().second!!, 0.0001)
    }

    @Test
    fun `an unreadable weight is never recorded as the target`() {
        render()

        // The one entry the sanitiser still lets through, and the one that used
        // to be indistinguishable from an empty field.
        typeWeight(",")

        compose.onNodeWithText("Log set").assertIsNotEnabled()
        compose.onNodeWithText("Log set").performClick()

        assertTrue("Nothing may be logged from an unreadable weight", logged.isEmpty())
        compose.onNodeWithText("Enter a number, like 12.5").assertExists()
    }

    @Test
    fun `correcting an unreadable weight makes it loggable again`() {
        render()

        typeWeight(",")
        compose.onNodeWithText("Log set").assertIsNotEnabled()

        typeWeight("2,5")
        compose.onNodeWithText("Log set").assertIsEnabled()
        logSet()

        assertEquals(2.5, logged.single().second!!, 0.0001)
    }

    @Test
    fun `a second separator never reaches the field`() {
        render()

        typeWeight("1.2.3")
        logSet()

        assertEquals("The stray separator is dropped, not parsed", 1.23, logged.single().second!!, 0.0001)
    }

    @Test
    fun `pounds are converted before they are recorded`() {
        // §7 stores kilograms whatever the field was typed in, so the screen has
        // to hand kilograms up regardless of what it drew.
        render(units = UnitSystem.IMPERIAL)

        typeWeight("100")
        logSet()

        assertEquals(45.359237, logged.single().second!!, 0.0001)
    }

    @Test
    fun `a comma in pounds converts too`() {
        render(units = UnitSystem.IMPERIAL)

        typeWeight("2,2")
        logSet()

        assertEquals(0.9979, logged.single().second!!, 0.001)
    }

    private fun typeWeight(text: String) {
        // The label carries the unit, so the finder has to as well.
        val field = compose.onNodeWithText("Weight ($unitLabel)", substring = true)
        field.performTextReplacement("")
        field.performTextInput(text)
    }

    private fun logSet() = compose.onNodeWithText("Log set").performClick()

    private fun render(units: UnitSystem = UnitSystem.METRIC) {
        unitLabel = if (units == UnitSystem.IMPERIAL) "lb" else "kg"
        compose.setContent {
            RepForthPreviewHost {
                CompositionLocalProvider(LocalUnitSystem provides units) {
                    SessionScreen(
                        state = SessionUiState(snapshot = ACTIVE, loading = false),
                        onCompleteSet = { reps, weight, duration ->
                            logged += Triple(reps, weight, duration)
                        },
                        onSkipSet = {},
                        onSkipRest = {},
                        onPause = {},
                        onResume = {},
                        onFinish = { _, _ -> },
                        onAbandon = {},
                        onKeepRunningSession = {},
                        onDiscardRunningAndStart = {},
                    )
                }
            }
        }
    }

    private companion object {
        val ACTIVE = SessionSnapshot(
            sessionId = "s1",
            templateId = "push-day",
            phase = SessionPhase.ACTIVE,
            phaseBeforePause = null,
            exercises = listOf(
                SessionExercise(
                    id = "e0",
                    exerciseId = ExerciseId("ex-0"),
                    position = 0,
                    // A planned weight, so "as prescribed" has something to be
                    // mistaken for.
                    target = ExerciseTarget.Reps(sets = 3, reps = 10, weightKg = 60.0),
                    restMs = 60_000L,
                ),
            ),
            currentExerciseIndex = 0,
            currentSetIndex = 0,
            startedAt = 1_767_225_600_000L,
        )
    }
}
