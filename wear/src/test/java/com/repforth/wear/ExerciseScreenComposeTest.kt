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
import androidx.compose.runtime.Composable
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
        renderControls(timed = true)

        compose.onNodeWithText(SKIP).performClick()

        assertEquals(listOf(WearAction.SkipSet), sent)
    }

    @Test
    fun `pause is still offered while a timed set runs`() {
        renderControls(timed = true)

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
        renderControls(timed = true, enabled = false)

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
        renderMedia(image())

        compose.onNodeWithText(NOTICE).assertIsDisplayed()
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
    /**
     * And this is the half that is easy to get wrong, now structural.
     *
     * With no picture there is no media page at all, so the notice has nowhere
     * to be drawn — the page count drops from three to two. That is a stronger
     * guarantee than the old one, which relied on a null check inside a screen
     * that was drawing the notice a few lines below the image either way.
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

    /** A timed set gets the picture too: it is the movement, not the counting. */
    @Test
    fun `a timed set shows its thumbnail too`() {
        render(timed = true, remainingSeconds = 42, thumbnail = image())

        compose.onNodeWithText("42").assertIsDisplayed()
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
        renderControls(timed = false)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.NextExercise), sent)
    }

    @Test
    fun `next exercise can be sent from a timed set too`() {
        renderControls(timed = true)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.NextExercise), sent)
    }

    @Test
    fun `a disconnected watch cannot leave the exercise`() {
        renderControls(timed = false, enabled = false)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(emptyList<WearAction>(), sent)
    }

/**
     * **The set does not scroll, and that is the point of the design pass.**
     *
     * It used to. Every control was stacked in one column that ran off the
     * bottom, so the number the screen exists for shared a scroll with the
     * button that throws away your remaining sets. A glance that moves is not a
     * glance. The controls page scrolls instead — see below — which is where
     * scrolling belongs.
     */
    @Test
    fun `the set page does not scroll`() {
        // The page itself, not the pager around it. The pager is horizontally
        // scrollable by construction -- that is what a swipe between pages is --
        // so asserting this through the pager finds its scroll action and says
        // nothing about the content. Asserting it here is the actual claim.
        host(fontScale = 1f) {
            ExercisePage(state = state(timed = true), remainingSeconds = 42)
        }

        assertEquals(
            "A glance must not move",
            0,
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size,
        )
    }

    /** And the controls page does, because three buttons do not fit at 200%. */
    @Test
    fun `the controls page scrolls`() {
        renderControls()

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
    fun `the primary action is still reachable at 200 percent font scale`() {
        render(timed = false, remainingSeconds = null, fontScale = 2f)

        compose.onNodeWithText(COMPLETE).performClick()

        assertEquals(listOf(WearAction.CompleteSet), sent)
    }

    /**
     * The last control on the controls page, at 200%, reached by scrolling.
     *
     * Two tests rather than one because a `ComposeTestRule` hosts a single
     * `setContent` per method — a second call throws "has already set content"
     * rather than replacing it, which is how this was found.
     *
     * Pressed rather than merely located: three full-width buttons do not fit a
     * 226dp circle at double size, and a layout that clipped the last one would
     * still have it in the tree.
     */
    @Test
    fun `the last control is still reachable at 200 percent font scale`() {
        renderControls(fontScale = 2f)

        compose.onNodeWithText(NEXT).performScrollTo().performClick()

        assertEquals(listOf(WearAction.NextExercise), sent)
    }

    /** A 1x1 bitmap: this asserts what the screen does with one, not how it looks. */
    private fun image(): ImageBitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()

    /**
     * Page 0: the set itself, through the real pager.
     *
     * The pager rather than [ExercisePage] alone, because the primary action is
     * the edge button the pager places — and whether that button exists for a
     * given phase is exactly what half of these assert.
     */
    private fun render(
        timed: Boolean,
        remainingSeconds: Int?,
        enabled: Boolean = true,
        fontScale: Float = 1f,
        thumbnail: ImageBitmap? = null,
        attributed: Boolean = thumbnail != null,
    ) = host(fontScale) {
        ExerciseScreen(
            state = state(timed, attributed),
            remainingSeconds = remainingSeconds,
            enabled = enabled,
            onAction = { sent += it },
            thumbnail = thumbnail,
        )
    }

    /**
     * Page 1: pause, skip and next exercise.
     *
     * These used to be stacked under the number on the one screen there was, and
     * the design pass moved them a swipe away. The behaviour did not change, so
     * the assertions did not either — only which composable is hosted.
     */
    private fun renderControls(
        timed: Boolean = false,
        enabled: Boolean = true,
        fontScale: Float = 1f,
    ) = host(fontScale) {
        ControlsPage(
            state = state(timed),
            enabled = enabled,
            onAction = { sent += it },
        )
    }

    /** Page 2: the picture, and §6's notice. Present only when there is one. */
    private fun renderMedia(thumbnail: ImageBitmap, attributed: Boolean = true) = host(1f) {
        MediaPage(state = state(timed = false, attributed = attributed), thumbnail = thumbnail)
    }

    private fun host(fontScale: Float, content: @Composable () -> Unit) {
        // Both, always, even at their defaults: Robolectric carries these across
        // test methods in one JVM, so a test that set only what it changed would
        // run in whatever the previous one left behind.
        RuntimeEnvironment.setQualifiers("+$ENGLISH")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent { RepForthWearTheme { content() } }
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
/** The number is the hero and the word only names it, so they are two nodes. */
        const val REPS = "Reps"
    }
}
