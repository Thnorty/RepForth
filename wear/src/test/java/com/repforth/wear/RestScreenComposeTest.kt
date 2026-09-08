package com.repforth.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.onNodeWithText("Next: barbell squat").assertIsDisplayed()
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
    ) {
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent {
            MaterialTheme {
                RestScreen(
                    state = state(),
                    remainingSeconds = remainingSeconds,
                    enabled = enabled,
                    onAction = { sent += it },
                )
            }
        }
    }

    private fun state() = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = WearPhase.Rest,
        exerciseId = "0025",
        exerciseName = "front plank",
        setNumber = 1,
        totalSets = 3,
        targetReps = 12,
        targetDurationMs = null,
        restDeadlineElapsedRealtimeMs = null,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = "barbell squat",
    )

    private companion object {
        const val SKIP_REST = "Skip rest"
        const val NEXT = "Next exercise"
    }
}
