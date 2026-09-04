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
 * The words come from `AppText`, which reads the app's resources. Nothing here
 * spells a user-visible string; see that file for what happened when it did.
 */
internal fun ComposeTestRule.completeOnboardingIfShown() {
    awaitFirstScreen()
    if (onAllNodes(onboardingShowing()).fetchSemanticsNodes().isEmpty()) {
        return
    }

    onNodeWithText(AppText.goalStrength).performClick()
    onNodeWithText(AppText.onboardingNext).performClick()
    // The experience chips are the level, not the span of years they used to
    // be. This walk kept tapping the old wording for days, and the compiler had
    // nothing to say about it — the reason these strings are resolved rather
    // than typed.
    onNodeWithText(AppText.experienceIntermediate).performClick()

    // Walk the rest of the questionnaire on its defaults. Every remaining step
    // advances without an answer, which is itself the thing being relied on.
    repeat(REMAINING_ONBOARDING_STEPS) {
        waitForIdle()
        if (onAllNodesWithText(AppText.onboardingNext).fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithText(AppText.onboardingNext).performClick()
        }
    }
    waitForIdle()
    if (onAllNodesWithText(AppText.onboardingFinish).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithText(AppText.onboardingFinish).performClick()
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
        onAllNodes(onboardingShowing()).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText(AppText.today).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * The questionnaire's first step, and so the marker for "onboarding is up".
 *
 * `substring` because the title is a heading inside a step that also carries
 * its own chips and progress text; the assertion is that the step is on screen,
 * not that it is the only thing on it.
 */
private fun onboardingShowing() = hasText(AppText.onboardingGoalTitle, substring = true)

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
    openTab(AppText.plans)
    // By description, not by text: Material3 clears the extended FAB's
    // inner semantics, so the words on the button are not in the tree.
    onNodeWithContentDescription(AppText.newWorkout).performClick()
    waitForIdle()
}

/** Goal and experience are answered explicitly; the rest take their defaults. */
private const val REMAINING_ONBOARDING_STEPS = 7

/** Opening the packaged catalog on a cold start is not instant. */
private const val FIRST_SCREEN_TIMEOUT_MS = 20_000L
