package com.repforth.wear

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_COMPARISON
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.WATCH_SCREENSHOT_DEVICE
import com.repforth.core.testing.WATCH_SMALL_ROUND_DEVICE
import com.repforth.core.testing.WATCH_SQUARE_DEVICE
import com.repforth.core.testing.screenshotPath
import com.repforth.core.wearprotocol.WearPhase
import com.repforth.core.wearprotocol.WearWorkoutState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the watch actually looks like — the first pictures this module has had.
 *
 * `wear/src/test` was string parity, then behaviour, and behaviour cannot see a
 * layout. Everything these catch is the kind of defect the phone's goldens were
 * written for and a wrist makes worse: 226dp is a quarter of a phone's width,
 * Turkish runs 15–30% longer than English, and a round display clips the corners
 * a square one lets you use.
 *
 * **One of these closes a gap named in the commit that created it.** The
 * thumbnail's presence was tested and its *drawing* was not: a decorative
 * `Image` carries no content description, so it leaves no node to query and
 * deleting the call would have kept every assertion green. A golden is the only
 * artefact that disagrees, and `exercise-thumbnail` is that artefact.
 *
 * The matrix is deliberately not every screen × every shape. The three message
 * screens are a sentence and a title; the two that carry controls, a countdown
 * and a picture are the ones where something can overflow, so those get the
 * shapes, the second language and the font scale.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = WATCH_SCREENSHOT_DEVICE)
class WearScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    // ---- The five screens §11 names ----

    @Test
    fun disconnected() = capture("disconnected") {
        DisconnectedScreen(lastSeen = state())
    }

    @Test
    fun no_workout() = capture("no-workout") { NoWorkoutScreen() }

    @Test
    fun finished() = capture("finished") { FinishedScreen(state(WearPhase.Finished)) }

    /** Abandoned is not finished, and the wording differs. */
    @Test
    fun abandoned() = capture("abandoned") { FinishedScreen(state(WearPhase.Abandoned)) }

    @Test
    fun exercise() = capture("exercise") { Exercise() }

    @Test
    fun rest() = capture("rest") { Rest() }

    // ---- The timed set, whose hero number is a clock ----

    @Test
    fun exercise_timed() = capture("exercise-timed") { Exercise(timed = true) }

    // ---- The picture, and the notice §6 requires beside it ----

    /**
     * The one that proves the image is drawn at all.
     *
     * Every other assertion about the thumbnail is about what the screen decides
     * — show the notice, or do not. This is the only one that would notice the
     * `Image` call disappearing.
     */
    @Test
    fun exercise_thumbnail() = capture("exercise-thumbnail") {
        Exercise(thumbnail = swatch())
    }

    // ---- The pages the design pass added ----

    /**
     * The between-sets decisions, which used to crowd the set itself.
     */
    @Test
    fun controls() = capture("controls") {
        ControlsPage(state = state(), enabled = true, onAction = {})
    }

    /** The same page during a rest, where skipping a *set* is meaningless. */
    @Test
    fun controls_resting() = capture("controls-resting") {
        ControlsPage(state = state(WearPhase.Rest), enabled = true, onAction = {})
    }

    /** Paused, which is the only state that renames the primary control. */
    @Test
    fun controls_paused() = capture("controls-paused") {
        ControlsPage(state = state(WearPhase.Paused), enabled = true, onAction = {})
    }

    /**
     * The picture at the size a picture is worth sending at.
     *
     * §6's notice rides on it, so this is also the golden that would notice the
     * attribution disappearing from the one screen that shows the imagery.
     */
    @Test
    fun media() = capture("media") {
        MediaPage(state = state(attributed = true), thumbnail = swatch())
    }

    /**
     * The whole thing assembled, for the page indicator and the edge button.
     *
     * The only capture that goes through the pager. What it is here to catch is
     * the frame -- that there are three dots and a button hugging the bottom
     * curve -- rather than the content, which the page captures above own.
     */
    @Test
    fun pager() = capture("pager") {
        ExerciseScreen(
            state = state(attributed = true),
            remainingSeconds = null,
            enabled = true,
            onAction = {},
            thumbnail = swatch(),
        )
    }

    /**
     * The worst case this layout has, and the one that was missing.
     *
     * Every other 200% capture is a page on its own, with the whole circle to
     * itself. Only here does the tallest possible text compete with the 73dp the
     * edge button takes — and the button is what the page has to be centred
     * around. The two-line exercise name was landing under "Complete" before
     * the reserved room was measured rather than guessed, and no golden could
     * have shown it.
     */
    @Test
    fun pager_large_text() = capture("pager-2x", fontScale = 2f) {
        ExerciseScreen(
            state = state(),
            remainingSeconds = null,
            enabled = true,
            onAction = {},
        )
    }

    // ---- Turkish, which is longer ----

    @Test
    fun exercise_turkish() = capture("exercise-tr", locale = TURKISH) { Exercise() }

    @Test
    fun rest_turkish() = capture("rest-tr", locale = TURKISH) { Rest() }

    // ---- 200% font scale, which §13 requires and this screen once could not survive ----

    @Test
    fun exercise_large_text() = capture("exercise-2x", fontScale = 2f) { Exercise() }

    @Test
    fun rest_large_text() = capture("rest-2x", fontScale = 2f) { Rest() }

    // ---- The other two shapes §11 asks for previews of ----

    @Test
    @Config(qualifiers = WATCH_SMALL_ROUND_DEVICE)
    fun exercise_small_round() = capture("exercise-small-round") { Exercise() }

    @Test
    @Config(qualifiers = WATCH_SQUARE_DEVICE)
    fun exercise_square() = capture("exercise-square") { Exercise() }

    @Test
    @Config(qualifiers = WATCH_SMALL_ROUND_DEVICE)
    fun rest_small_round() = capture("rest-small-round") { Rest() }

    // ---- Fixtures ----

    /**
     * Page 0 of a set, drawn directly rather than through the pager.
     *
     * A golden of the pager photographs page 0 and says nothing about the other
     * two, so each page is captured on its own and the pager gets one picture of
     * its own for the indicator. That also keeps these stable: a page rendered
     * inside a pager is offset and scaled by whatever the pager is doing.
     */
    @Composable
    private fun Exercise(timed: Boolean = false, thumbnail: ImageBitmap? = null) {
        ExercisePage(
            state = state(timed = timed, attributed = thumbnail != null),
            remainingSeconds = if (timed) 42 else null,
        )
    }

    @Composable
    private fun Rest() {
        RestPage(
            state = state(WearPhase.Rest, next = "Barbell Squat"),
            remainingSeconds = 45,
        )
    }

    /**
     * A deterministic stand-in for a downloaded thumbnail.
     *
     * Four flat quadrants rather than a real photograph: it is unmistakably an
     * image when it renders, it is identical on every machine, and it carries no
     * licensed bytes into the repository — §6 forbids committing media, and a
     * golden is committed.
     */
    private fun swatch(): ImageBitmap {
        val size = 8
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val left = x < size / 2
                val top = y < size / 2
                bitmap.setPixel(
                    x,
                    y,
                    when {
                        left && top -> Color.rgb(0xE8, 0x6A, 0x33)
                        left -> Color.rgb(0x2E, 0x2E, 0x2E)
                        top -> Color.rgb(0x9A, 0x9A, 0x9A)
                        else -> Color.rgb(0xF2, 0xF2, 0xF2)
                    },
                )
            }
        }
        return bitmap.asImageBitmap()
    }

    /**
     * A snapshot as it arrives on the wire, which is why the name is capitalised.
     *
     * The dataset stores names in lower case and the phone applies
     * `exerciseDisplayName` before publishing, so the watch never sees "barbell
     * decline wide-grip press" -- it sees the title case here. These fixtures
     * said otherwise until 2026-09-10, which made every wear golden slightly
     * narrower than the real thing: capitals are wider, and the width is the
     * whole point of a 226dp render.
     */
    private fun state(
        phase: WearPhase = WearPhase.Exercise,
        timed: Boolean = false,
        attributed: Boolean = false,
        next: String? = null,
    ) = WearWorkoutState(
        sessionId = "today",
        revision = 7,
        phase = phase,
        exerciseId = "0025",
        exerciseName = "Barbell Decline Wide-Grip Press",
        setNumber = 2,
        totalSets = 4,
        targetReps = if (timed) null else 12,
        targetDurationMs = if (timed) 60_000L else null,
        restDeadlineElapsedRealtimeMs = null,
        setDeadlineElapsedRealtimeMs = null,
        nextExerciseName = next,
        mediaAttribution = "© Gym visual — https://gymvisual.com/".takeIf { attributed },
        // Nine sets in, of twenty-two: far enough round for the arc to be
        // unmistakably a fraction rather than empty or full, which is what a
        // golden of a ring has to be able to fail on.
        setsCompleted = 9,
        setsTotal = 22,
        exerciseNumber = 3,
        exerciseCount = 6,
        restTotalMs = if (phase == WearPhase.Rest) 60_000L else null,
    )

    private fun capture(
        name: String,
        locale: String = ENGLISH,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        // Both, always, even at their defaults. Robolectric carries these across
        // test methods in one JVM, so a test that set only what it changed would
        // render in whatever the previous one left behind — the phone's English
        // goldens came out Turkish that way once, and passed when the class ran
        // alone.
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(fontScale)

        // `AppScaffold`, because that is what `WearMainActivity` puts around every
        // screen -- and because it is what paints the background. Without it the
        // first render of this class came out as dark-theme text on a near-white
        // window: white on white, unreadable, and a golden that would have
        // happily accepted the app looking like that forever. The phone's
        // `RepForthPreviewHost` exists for exactly the same reason.
        //
        // With an empty `timeText`, for two reasons that happen to agree. Its
        // default draws the clock as curved text, and `WarpedCurvedTextRenderer`
        // calls a native address method Robolectric cannot provide -- every test
        // in this class died with a `LinkageError` until this argument was
        // passed. And a clock in a golden is a golden that fails at the next
        // minute anyway.
        compose.setContent {
            RepForthWearTheme {
                AppScaffold(timeText = {}) { content() }
            }
        }
        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }
}
