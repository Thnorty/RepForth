package com.repforth.wear

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    // ---- §3's thumbnail, and the notice that has to come with it ----
    //
    // These assert what the screen *decides* about the picture, not that it
    // draws one. A decorative Image has no content description — deliberately;
    // the exercise name is the next line and a screen reader should not say it
    // twice — so it leaves no node to query. Proving the pixels needs a wear
    // golden, which this module does not have yet; review item 4.2 asks for that
    // and it is on the backlog.

    /**
     * The picture is optional and the screen is not.
     *
     * Null covers every reason at once — the phone has not cached it, the
     * manifest has no entry, the user restricted downloads to Wi-Fi — and the
     * screen does the same thing for all of them, which is what the phone does:
     * carry on with the name.
     */
    @Test
    fun `an exercise with no thumbnail still draws everything else`() {
        render(timed = false, remainingSeconds = null, thumbnail = null)

        compose.onNodeWithText(NAME).assertIsDisplayed()
        compose.onNodeWithText(COMPLETE).assertIsDisplayed()
    }

    /**
     * §6, and the licence it comes from: the notice goes wherever the imagery
     * does. This is the half that is easy to get right.
     */
    @Test
    fun `a thumbnail brings its attribution with it`() {
        render(timed = false, remainingSeconds = null, thumbnail = image())

        compose.onNodeWithText(NOTICE).performScrollTo().assertIsDisplayed()
    }

    /**
     * And this is the half that is easy to get wrong.
     *
     * A copyright line under an exercise showing no picture is a claim about
     * something the user cannot see. The phone only sends the notice with an
     * asset, and the screen only draws it with a decoded bitmap — so a transfer
     * that arrived and failed to decode shows neither, rather than the notice
     * alone.
     */
    @Test
    fun `no thumbnail means no attribution, even when the phone sent one`() {
        render(timed = false, remainingSeconds = null, thumbnail = null, attributed = true)

        assertEquals(
            "Attribution without imagery is a claim about nothing",
            0,
            compose.onAllNodesWithText(NOTICE).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `a timed set shows its thumbnail too`() {
        render(timed = true, remainingSeconds = 42, thumbnail = image())

        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText(NOTICE).performScrollTo().assertIsDisplayed()
    }

    // ---- Leaving the exercise, which had no button at all ----

    /**
     * `WearAction.NextExercise` was unreachable.
     *
     * It has been in the protocol since it was written, maps to a phone command,
     * and §3 lists it in the watch MVP — and no screen offered it. The protocol's
     * own standard for the action set is "nothing duplicated and nothing
     * unreachable"; a member nothing can send fails the second half, and nothing
     * in the suite could notice because every test asked about the buttons that
     * *were* drawn.
     */
    @Test
    fun `next exercise can be sent from a running set`() {
        render(timed = false, remainingSeconds = null)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.NextExercise), sent)
    }

    @Test
    fun `next exercise can be sent from a timed set too`() {
        render(timed = true, remainingSeconds = 42)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.NextExercise), sent)
    }

    @Test
    fun `a disconnected watch cannot leave the exercise`() {
        render(timed = false, remainingSeconds = null, enabled = false)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(emptyList<WearAction>(), sent)
    }

    /**
     * The third control does not fit, and that is why the screen scrolls.
     *
     * The exercise screen already spent about 194 of the watch's 216dp before
     * this. A fixed container would simply have cut the new button off the
     * bottom with no way to reach it — which is the failure this asserts is
     * gone, rather than the presence of a scrollbar for its own sake.
     */
    @Test
    fun `the screen scrolls`() {
        render(timed = true, remainingSeconds = 42)

        compose.onNode(hasScrollAction()).assertExists()
    }

    /**
     * §13 requires text to survive 200% scaling, and this screen did not.
     *
     * The old container was a fixed-size box: anything that grew past the
     * display was cut off, with nothing to scroll. Every control has to remain
     * reachable, not merely present in the tree — so this scrolls to the last
     * one and presses it, which is the only version of the assertion that a
     * clipped layout would fail.
     */
    @Test
    fun `every control is still reachable at 200 percent font scale`() {
        render(timed = false, remainingSeconds = null, fontScale = 2f)

        compose.onNodeWithText(COMPLETE).performScrollTo().performClick()
        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.CompleteSet, WearAction.NextExercise), sent)
    }

    /** A 1x1 bitmap: this asserts what the screen does with one, not how it looks. */
    private fun image(): ImageBitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun render(
        timed: Boolean,
        remainingSeconds: Int?,
        enabled: Boolean = true,
        fontScale: Float = 1f,
        thumbnail: ImageBitmap? = null,
        attributed: Boolean = thumbnail != null,
    ) {
        // Both, always, even at their defaults: Robolectric carries these across
        // test methods in one JVM, so a test that set only what it changed would
        // run in whatever the previous one left behind.
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent {
            MaterialTheme {
                ExerciseScreen(
                    state = state(timed, attributed),
                    remainingSeconds = remainingSeconds,
                    enabled = enabled,
                    onAction = { sent += it },
                    thumbnail = thumbnail,
                )
            }
        }
    }

    private fun state(timed: Boolean, attributed: Boolean = false) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = WearPhase.Exercise,
        exerciseId = "0025",
        exerciseName = NAME,
        setNumber = 1,
        totalSets = 3,
        targetReps = if (timed) null else 12,
        targetDurationMs = if (timed) 60_000L else null,
        restDeadlineElapsedRealtimeMs = null,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = null,
        mediaAttribution = NOTICE.takeIf { attributed },
    )

    private companion object {
        const val NAME = "front plank"
        const val NOTICE = "© Gym visual — https://gymvisual.com/"
        const val COMPLETE = "Complete"
        const val SKIP = "Skip"
        const val PAUSE = "Pause"
        const val NEXT = "Next exercise"
        const val SECONDS = "Seconds"
        const val REPS = "12 reps"
    }
}
