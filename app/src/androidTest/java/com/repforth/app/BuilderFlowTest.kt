package com.repforth.app

import android.view.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        compose.onNodeWithText("Add exercise").performClick()
        compose.waitForIdle()
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

        compose.onNodeWithText("Save workout").performClick()
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
     */
    @Test
    fun coachFillsTheBuilderButSavesNothingUntilAsked() {
        compose.openNewWorkout()

        compose.onNodeWithText("Build one for me").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Build it").performClick()

        // Generation reads the whole catalog off disk, so it is not instant.
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText("Sets")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText(COACH_PLAN_NAME) and hasSetTextAction()).assertExists()

        // Leave without saving. Back, not a tab: the builder is not a
        // top-level destination, so it has no bottom bar to tap.
        compose.onNodeWithContentDescription("Back").performClick()
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
     */
    private fun awaitSettledSaveButton(): SemanticsNode {
        compose.waitUntil(IME_TIMEOUT_MS) { imeHeight() > 0 }
        compose.waitForIdle()

        var previous = Float.NaN
        var settled = 0
        compose.waitUntil(IME_TIMEOUT_MS) {
            val bottom = saveButton().boundsInWindow.bottom
            settled = if (bottom == previous) settled + 1 else 0
            previous = bottom
            settled >= STABLE_FRAMES
        }
        return saveButton()
    }

    private fun saveButton(): SemanticsNode =
        compose.onNodeWithText("Save workout").fetchSemanticsNode()

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

    private fun windowHeight(): Int {
        var height = 0
        compose.activityRule.scenario.onActivity { activity ->
            height = activity.window.decorView.height
        }
        return height
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L

        /** The keyboard animates in, and Samsung's takes its time. */
        const val IME_TIMEOUT_MS = 10_000L

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
