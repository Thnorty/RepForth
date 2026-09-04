package com.repforth.app

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Building a plan, all the way through.
 *
 * The three tests that came before this one open screens and assert they
 * opened. That was enough to catch a screen that crashes and nothing else — and
 * the defect that actually stopped the app being usable was a Save button that
 * composed, measured, reported itself enabled, and sat underneath the keyboard.
 * Every existing test passed while a workout could not be saved at all.
 *
 * So these interact: type, tap, save, and check the result reached the next
 * screen.
 *
 * **State is not reset between methods.** The user tables are a real on-disk
 * Room database and only the preferences are swapped for an in-memory fake, so
 * a plan saved by one test is still there for the next. Every assertion here
 * names its own plan rather than counting rows, which is the cheaper of the two
 * ways to be independent of that.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BuilderFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hilt.inject()
        compose.completeOnboardingIfShown()
    }

    /**
     * The regression guard for the defect this file exists because of.
     *
     * `assertIsDisplayed()` is deliberately not the assertion. The app is
     * edge-to-edge, so the window is never resized when the keyboard opens:
     * Compose's root keeps its full height, the button stays inside it, and
     * `assertIsDisplayed` passes happily while the button is hidden under the
     * IME. The only thing that knows the difference is the window insets.
     *
     * So this asks the platform where the keyboard is and asserts the button
     * ends above it. Watched failing with the `safeDrawing` padding removed
     * from the shell.
     *
     * **On the emulator the keyboard sometimes does not open at all**, which is
     * what [awaitWindowFocus] is for. Nothing is wrong with the screen when that
     * happens — the window simply does not hold focus yet, and an unfocused
     * window cannot raise a keyboard.
     */
    @Test
    fun theSaveButtonStaysAboveTheKeyboard() {
        compose.openNewWorkout()

        // Focusing the name field is what raises the IME, and is exactly what
        // someone does first on this screen.
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Keyboard test")

        val save = awaitSettledSaveButton()
        val keyboardTop = windowHeight() - imeHeight()

        assertTrue(
            "Save workout ends at ${save.boundsInWindow.bottom} but the keyboard " +
                "starts at $keyboardTop, so it is underneath and cannot be tapped",
            save.boundsInWindow.bottom <= keyboardTop,
        )
    }

    /**
     * A plan built by hand reaches the library.
     *
     * The end-to-end path that was broken: the builder composed correctly, the
     * ViewModel saved correctly, and the button in between could not be
     * pressed. Only something that presses it can tell.
     */
    @Test
    fun aPlanSavedInTheBuilderAppearsInPlans() {
        val name = "Instrumented plan"
        compose.openNewWorkout()

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(name)

        compose.onNodeWithText(AppText.addExercise).performClick()
        // Wait for the picker's own search field, not just for idle. The line
        // below takes "the one text field on screen", so a picker that has not
        // opened yet sends the catalog query into the workout's name field and
        // the failure arrives fifteen seconds later as "the catalog has no
        // bench press" -- which is a lie about the catalog.
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(AppText.pickSearch).fetchSemanticsNodes().isNotEmpty()
        }
        // A prefix, not the whole name. `hasText` matches a text field's own
        // contents too, so searching for the exact name makes the search box
        // itself a match — and the click lands in the box rather than on the
        // result, which looks identical until the next assertion fails.
        compose.onNode(hasSetTextAction()).performTextInput(CATALOG_QUERY)
        val result = hasText(CATALOG_EXERCISE) and !hasSetTextAction()
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(result).onFirst().performClick()
        compose.waitForIdle()

        // A row opens the exercise's detail sheet; the sheet's pinned button is
        // what adds it. Tapping the row used to add it outright, and this test
        // still believed that -- so it went looking for "Save workout" while the
        // picker was still on screen and the sheet was over the top of it, and
        // reported that the builder had no save button.
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(AppText.addToWorkout).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(AppText.addToWorkout).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(AppText.saveWorkout).performClick()
        compose.waitForIdle()

        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(name).assertIsDisplayed()
    }

    /**
     * Coach fills the builder and writes nothing.
     *
     * The property that makes a generated plan safe to offer at all: it arrives
     * as an editable draft, and leaving without saving leaves no trace. If this
     * ever fails, Coach has started saving behind the user's back.
     *
     * **The answer comes from [TestGenerationModule], not from a provider.**
     * The real generator returns `NO_PROVIDER_CONFIGURATION` until a key is
     * stored, and no test device has one — so this waited fifteen seconds for a
     * draft that nothing could have produced, and the comment it waited under
     * ("generation reads the whole catalog off disk") described a local
     * generator the app no longer has. What is exercised here is the screen and
     * the discard, which is what could not be reached before; the generator's
     * own behaviour is tested in `core:ai`.
     */
    @Test
    fun coachFillsTheBuilderButSavesNothingUntilAsked() {
        compose.openNewWorkout()

        compose.onNodeWithText(AppText.coachOpen).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(AppText.coachGenerate).performClick()

        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText(AppText.sets)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(COACH_PLAN_NAME) and hasSetTextAction()).assertExists()

        // Leave without saving. Back, not a tab: the builder is not a
        // top-level destination, so it has no bottom bar to tap.
        compose.onNodeWithContentDescription(AppText.back).performClick()
        compose.waitForIdle()

        // A generated draft is unsaved work, so leaving asks first -- the top
        // bar's arrow dispatches a real back press rather than popping the back
        // stack, which is what puts it through the builder's `BackHandler`.
        // Confirming is the path being tested: it is the one that must not
        // write anything.
        compose.onNodeWithText(AppText.discard).performClick()
        compose.waitForIdle()

        assertTrue(
            "Coach saved a plan that was never confirmed",
            compose.onAllNodes(hasText(COACH_PLAN_NAME)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * Waits for the keyboard *and* for the layout to have moved out of its way.
     *
     * `waitUntil { imeHeight() > 0 }` is not enough, and the difference cost a
     * red build on a screen that was demonstrably correct in a screenshot taken
     * on the same device a minute earlier. `rootWindowInsets` reports the IME's
     * final height the moment its window is created, which is several frames
     * before Compose has recomposed and re-laid-out around the new inset — so
     * the assertion compared a settled keyboard against the button's position
     * from before the padding applied, and read it at the bottom of the window.
     *
     * Waiting for the bounds to stop moving is deliberately not the same as
     * waiting for them to be correct. A build where the shell does not pad its
     * bottom edge settles immediately, at the wrong place, and the assertion
     * below still fails — which is the whole point of the test. Re-checked by
     * removing the padding from the shell: still red, and for the right reason.
     *
     * Each half fails with its own message. When both were one
     * `ComposeTimeoutException` they were indistinguishable, and a flake in the
     * first half was read for two runs as the layout never settling.
     */
    private fun awaitSettledSaveButton(): SemanticsNode {
        awaitKeyboard()
        compose.waitForIdle()

        var previous = Float.NaN
        var settled = 0
        try {
            compose.waitUntil(IME_TIMEOUT_MS) {
                val bottom = saveButton().boundsInWindow.bottom
                settled = if (bottom == previous) settled + 1 else 0
                previous = bottom
                settled >= STABLE_FRAMES
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError(
                "The save button never stopped moving: last bottom $previous, " +
                    "keyboard ${imeHeight()}px in a ${windowHeight()}px window",
                timeout,
            )
        }
        return saveButton()
    }

    /**
     * Raises the software keyboard, asking more than once if it has to.
     *
     * Tapping the field is a request, not a guarantee. On the emulator the
     * first tap is sometimes dropped — LatinIME is cold, or the window has not
     * taken focus yet — and the IME inset simply stays at zero, which is
     * indistinguishable from a keyboard that opened somewhere the app cannot
     * see. Two runs of the full suite disagreed about this test on an unchanged
     * build, which is precisely the flake that keeps a suite out of CI.
     *
     * So it asks again rather than only waiting longer — though it does both,
     * because a starved emulator is slow at everything and two runs did fail
     * here at ten seconds.
     *
     * **It is not the AVD's hardware keyboard**, which was the first
     * explanation and was wrong twice over. `hw.keyboard = no` in the managed
     * device's `config.ini`, so `show_ime_with_hard_keyboard` has nothing to
     * suppress; setting it changed nothing, and the run that first appeared to
     * vindicate it had it set and failed anyway. The message below still reports
     * the setting, because a device where it *is* the cause would say so there.
     *
     * The retrying is kept even though [awaitWindowFocus] is the actual fix: it
     * costs nothing when the keyboard opens, and the first tap being dropped is
     * a real thing on a slow device.
     */
    private fun awaitKeyboard() {
        awaitWindowFocus()
        repeat(IME_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                compose.onNode(hasSetTextAction()).performClick()
                compose.waitForIdle()
            }
            requestKeyboard()
            val deadline = SystemClock.uptimeMillis() + IME_ATTEMPT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                if (imeHeight() > 0) return
                SystemClock.sleep(IME_POLL_MS)
            }
        }
        throw AssertionError(
            "The keyboard never opened after $IME_ATTEMPTS attempts. " +
                "Enabled IMEs: [${shell("ime list -s")}], current: " +
                "[${shell("settings get secure default_input_method")}], " +
                "show_ime_with_hard_keyboard: " +
                "[${shell("settings get secure show_ime_with_hard_keyboard")}], " +
                "showSoftInput: [$lastShowSoftInput], " +
                "window focus: [${hasWindowFocus()}], " +
                "focused window: [${focusedWindow()}], " +
                "state: [${inputMethodState()}]",
        )
    }

    /**
     * Waits for the activity's window to actually hold focus.
     *
     * **An unfocused window cannot raise a keyboard**, and that is what was
     * happening: `showSoftInput` returned false, the IME stayed bound and
     * unshown, and twenty-four seconds of tapping the field changed nothing.
     * The measurement that named it came from Espresso, of all things —
     * `RootViewWithoutFocusException` printed the decor view with
     * `has-window-focus=false` while `dumpsys input_method` was still naming
     * this app as its current client, which is why the input-method dump alone
     * read as a healthy binding for several runs.
     *
     * It is intermittent, and it is the emulator rather than the app: a window
     * that has just replaced another one does not always have focus by the time
     * the test thread gets to look.
     */
    private fun awaitWindowFocus() {
        val deadline = SystemClock.uptimeMillis() + WINDOW_FOCUS_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (hasWindowFocus()) return
            SystemClock.sleep(IME_POLL_MS)
        }
        // Fail here rather than falling through. Without this the run spent
        // another twenty-four seconds tapping a field that could not raise a
        // keyboard and then blamed the keyboard -- a headline that sent the
        // first two investigations of this failure in the wrong direction.
        throw AssertionError(
            "The activity's window never took focus in ${WINDOW_FOCUS_TIMEOUT_MS}ms, " +
                "so nothing could have opened a keyboard. Window manager says: " +
                "[${focusedWindow()}]",
        )
    }

    /**
     * What the window manager believes has focus.
     *
     * `dumpsys input_method` cannot answer this -- it names its current client,
     * which stays this app while the app sits unfocused. When the window is
     * unfocused for the whole wait rather than for a moment, something else is
     * holding it, and this is the only line that says what.
     */
    private fun focusedWindow(): String =
        shell("dumpsys window")
            .lineSequence()
            .filter { "mCurrentFocus" in it || "mFocusedApp" in it }
            .joinToString(" ; ") { it.trim() }
            .ifEmpty { "nothing reported" }

    private fun hasWindowFocus(): Boolean {
        var focused = false
        compose.activityRule.scenario.onActivity { activity ->
            focused = activity.window.decorView.hasWindowFocus()
        }
        return focused
    }

    /**
     * Asks the platform for the keyboard, on the focused editor.
     *
     * The tap alone is not reliable here, and `dumpsys input_method` says why:
     * after eight taps over twenty-four seconds the IME was bound, the field was
     * the current client, and `mInputShown=false` — the field had focus (text
     * typed into it arrives through the input connection whether a keyboard is
     * drawn or not) and Compose's request to show one had simply not reached
     * the input method manager.
     *
     * **This does not weaken the test.** What is asserted below is where the
     * save button sits relative to the keyboard, not that tapping a field
     * raises one. A keyboard is this test's precondition, so it is set up
     * deterministically rather than hoped for; "does tapping a field open the
     * keyboard" is a different assertion, and one no test here makes.
     */
    private fun requestKeyboard() {
        compose.activityRule.scenario.onActivity { activity ->
            val target = activity.currentFocus ?: return@onActivity
            lastShowSoftInput = activity.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(target, 0)
        }
    }

    /** What the last request returned, for the failure message. */
    private var lastShowSoftInput: Boolean? = null

    /** The handful of `dumpsys input_method` lines worth reading in a failure. */
    private fun inputMethodState(): String =
        shell("dumpsys input_method")
            .lineSequence()
            .filter { line ->
                INPUT_METHOD_FIELDS.any { line.contains(it) }
            }
            .joinToString(" ; ") { it.trim() }

    private fun saveButton(): SemanticsNode =
        compose.onNodeWithText(AppText.saveWorkout).fetchSemanticsNode()

    /** The bottom inset the keyboard currently occupies, in pixels. */
    private fun imeHeight(): Int {
        var height = 0
        compose.activityRule.scenario.onActivity { activity ->
            height = activity.window.decorView.rootWindowInsets
                ?.getInsets(WindowInsets.Type.ime())
                ?.bottom
                ?: 0
        }
        return height
    }

    /**
     * Runs one shell command and returns everything it printed.
     *
     * The output has to be read, not just closed: `executeShellCommand` hands
     * back the read end of a pipe, and a command whose output nobody drains can
     * block on a full one.
     */
    private fun shell(command: String): String {
        val pipe: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pipe).use { stream ->
            stream.bufferedReader().readText().trim()
        }
    }

    private fun windowHeight(): Int {
        var height = 0
        compose.activityRule.scenario.onActivity { activity ->
            height = activity.window.decorView.height
        }
        return height
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L

        /** Enough to tell a missing IME from an unfocused window. */
        val INPUT_METHOD_FIELDS = listOf(
            "mInputShown",
            "mImeWindowVis",
            "mBoundToMethod",
            "mHaveConnection",
            "mCurFocusedWindow",
            "mSystemReady",
            "mCurClient",
        )

        /** The keyboard animates in, and Samsung's takes its time. */
        const val IME_TIMEOUT_MS = 10_000L

        /** How long one request for the keyboard is given before asking again. */
        const val IME_ATTEMPT_MS = 3_000L

        /**
         * Requests before the keyboard is declared absent rather than slow.
         *
         * Eight, for twenty-four seconds in total, which is far more than a
         * keyboard needs and is sized for the emulator rather than for the
         * keyboard: two runs failed here at ten seconds while the machine was
         * under enough memory pressure to have a build killed, and a starved
         * emulator is slow at everything rather than broken.
         */
        const val IME_ATTEMPTS = 8

        /** Between inset readings. Short enough to be invisible when it works. */
        const val IME_POLL_MS = 100L

        /**
         * How long the window is given to take focus before the test gives up.
         *
         * Generous because the cost of being wrong is asymmetric: a run that
         * would have focused at eleven seconds is a false failure, and a run
         * that never focuses fails either way.
         */
        const val WINDOW_FOCUS_TIMEOUT_MS = 20_000L

        /** Consecutive identical readings before the layout counts as settled. */
        const val STABLE_FRAMES = 3

        /** A name that exists in the pinned catalog and is unambiguous. */
        const val CATALOG_EXERCISE = "barbell bench press"

        /** A prefix of it, so the search field's own text is not a match. */
        const val CATALOG_QUERY = "barbell bench pr"

        /** `coach_default_name`, used when the field is left empty. */
        const val COACH_PLAN_NAME = "Coach plan"
    }
}
