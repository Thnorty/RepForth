package com.repforth.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/**
 * The steps every instrumentation test has to take before it can test anything.
 *
 * Here rather than in one test class because the second test file needed all of
 * it, and a copy of the onboarding walk is a copy of the assumption that
 * onboarding has nine steps — which has already changed twice.
 *
 * These read English. The device under test runs `en-US`, and the alternative —
 * resolving strings from the feature modules' `R` classes — does not compile
 * from `androidTest`, since a module's `implementation` dependencies reach the
 * test at runtime but not at compile time. If this ever runs on a Turkish
 * device it will fail on the first `onNodeWithText`, loudly, which is the
 * failure worth having.
 */
internal fun ComposeTestRule.completeOnboardingIfShown() {
    awaitFirstScreen()
    if (onAllNodes(hasText(ONBOARDING_MARKER, substring = true)).fetchSemanticsNodes().isEmpty()) {
        return
    }

    onNodeWithText("Strength").performClick()
    onNodeWithText("Next").performClick()
    // "Intermediate", not "1 to 3 years". The experience chips were renamed to
    // the level rather than the span of years -- see `ProfileTerms.kt`, which
    // records the reason -- and this walk kept tapping the old text. Every
    // instrumentation test in this module failed on it from that moment, and
    // nothing said so for days because nothing ran them: a library module gets
    // a managed device from `repforth.android.instrumentation`, and `:app` had
    // none until it was added for `StartConflictTest`.
    onNodeWithText("Intermediate").performClick()

    // Walk the rest of the questionnaire on its defaults. Every remaining step
    // advances without an answer, which is itself the thing being relied on.
    repeat(REMAINING_ONBOARDING_STEPS) {
        waitForIdle()
        if (onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithText("Next").performClick()
        }
    }
    waitForIdle()
    if (onAllNodesWithText("Finish").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithText("Finish").performClick()
    }
    awaitFirstScreen()
}

/**
 * Waits until the app has decided what to show.
 *
 * `waitForIdle()` is not enough and the difference is not subtle. Until the
 * profile has been read, `MainActivity` renders nothing on purpose — the window
 * is already painted, so an empty frame is the launch screen continuing. That
 * state is driven by a Room flow, which Compose's idling resource knows nothing
 * about, so `waitForIdle()` returns immediately onto a semantics tree with a
 * root and no children.
 *
 * Every test built on that raced it: they asked whether onboarding was showing,
 * were told no because nothing at all was showing, and walked into a screen
 * that had not been drawn yet. It cost three failing tests and an hour, and the
 * symptom — a root node with no children — looks exactly like a screen that
 * failed to compose.
 */
internal fun ComposeTestRule.awaitFirstScreen() {
    waitUntil(FIRST_SCREEN_TIMEOUT_MS) {
        onAllNodes(hasText(ONBOARDING_MARKER, substring = true))
            .fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty()
    }
}

/** Opens a bottom-bar tab and waits for it to settle. */
internal fun ComposeTestRule.openTab(label: String) {
    onNodeWithText(label).performClick()
    waitForIdle()
}

/**
 * Opens an empty builder from the Plans tab.
 *
 * Plans is the only route to a new plan once one exists — Today's "Build a
 * workout" card is the empty state and disappears as soon as anything is
 * saved, so a test that went through Today would pass once and then stop
 * finding its own entry point.
 */
internal fun ComposeTestRule.openNewWorkout() {
    openTab("Plans")
    // By description, not by text: Material3 clears the extended FAB's
    // inner semantics, so the words on the button are not in the tree.
    onNodeWithContentDescription("New workout").performClick()
    waitForIdle()
}

/** Goal and experience are answered explicitly; the rest take their defaults. */
private const val REMAINING_ONBOARDING_STEPS = 7

/** Text unique to the questionnaire's first step. */
private const val ONBOARDING_MARKER = "training for"

/** Opening the packaged catalog on a cold start is not instant. */
private const val FIRST_SCREEN_TIMEOUT_MS = 20_000L
