package com.repforth.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.TemplateRepository
import com.repforth.feature.session.SessionController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tapping a plan while a different workout is running asks, and does not move.
 *
 * **This is the one test shape this project has been burned by.** Both halves of
 * #16 passed their unit tests, passed CI, merged, installed, and changed nothing
 * on the device: `SessionController.start` was tested directly while the screen
 * guarded the call behind `state.snapshot == null` and never made it. A green
 * test over a function nothing calls proves the function.
 *
 * So this asserts the wiring rather than the logic. `WorkoutStartViewModel` is
 * covered by its own unit tests; what nothing covered was whether the shell
 * reaches it — `onStartPlan = starter::request` in `RepForthNavHost`, and the
 * `LaunchedEffect` that navigates on the answer. Both are three lines of
 * composable glue, which is exactly where the last bug lived.
 *
 * It runs on the managed emulator rather than a phone:
 *
 * ```
 * ./gradlew :app:pixel6Api34PlaceholderDebugAndroidTest
 * ```
 *
 * **State is not reset between methods** — the user tables are a real on-disk
 * Room database and only the preferences are swapped. Every plan here is named
 * distinctly for that reason, and the running session is cleared in setup so a
 * previous method cannot decide this one's outcome.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StartConflictTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /**
     * Granted, because tapping the running plan opens the workout and the
     * workout starts a foreground service.
     *
     * Without it the emulator denies the permission, the service is refused,
     * and the activity dies -- which surfaces as "No compose hierarchies found
     * in the app", a message that describes the symptom and none of the cause.
     * A phone that has been through onboarding once has already granted this,
     * which is why it has never been seen on hardware.
     */
    @get:Rule(order = 1)
    val notifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var templates: TemplateRepository

    @Inject lateinit var controller: SessionController

    @Before
    fun setUp() {
        hilt.inject()
        compose.completeOnboardingIfShown()
    }

    /**
     * The conflict is raised on the plan list, and the app stays there.
     *
     * Both halves matter. A dialog that appeared *after* navigating into the
     * running workout was the behaviour that was reported as broken, so it is
     * not enough that the question is asked — it has to be asked here.
     */
    @Test
    fun startingADifferentPlanAsksBeforeLeavingTheList() {
        givenRunning(RUNNING, alsoSaved = REQUESTED)

        compose.openTab(AppText.plans)
        compose.startPlan(REQUESTED)

        compose.onNodeWithText(AppText.conflictTitle).assertIsDisplayed()

        // Still on Plans: the workout screen is not underneath the dialog.
        // Counted as "some", not "two" -- the database outlives this class and
        // other test classes save plans of their own, so an exact count would
        // fail for a reason that has nothing to do with this behaviour.
        assertTrue(
            "The app must not have navigated into a workout",
            compose.onAllNodesWithText(AppText.startPlan).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * Nothing is decided by asking.
     *
     * If raising the question started, abandoned or swapped anything, "go back
     * to it" would be answering about a workout that no longer existed.
     */
    @Test
    fun askingLeavesTheRunningWorkoutAlone() {
        givenRunning(RUNNING, alsoSaved = REQUESTED)
        val before = controller.state.value?.sessionId

        compose.openTab(AppText.plans)
        compose.startPlan(REQUESTED)
        compose.onNodeWithText(AppText.conflictTitle).assertIsDisplayed()

        assertEquals(before, controller.state.value?.sessionId)
        assertEquals(RUNNING, controller.state.value?.templateId)
    }

    /** Tapping the plan that is already running is not a conflict. */
    @Test
    fun startingTheRunningPlanOpensItWithoutAsking() {
        givenRunning(RUNNING, alsoSaved = REQUESTED)

        compose.openTab(AppText.plans)
        compose.startPlan(RUNNING)
        compose.waitForIdle()

        assertEquals(
            "Tapping the running plan must not raise the question",
            0,
            compose.onAllNodesWithText(AppText.conflictTitle).fetchSemanticsNodes().size,
        )
    }

    /** Saves both plans and leaves [templateId] running. */
    private fun givenRunning(templateId: String, alsoSaved: String) = runBlocking {
        templates.save(plan(templateId))
        templates.save(plan(alsoSaved))

        // `abandonAndStart` rather than `start`, because a session left running
        // by a previous method would make `start` refuse and this method would
        // silently set up the wrong state. It is a no-op when nothing is
        // running.
        controller.abandonAndStart(templateId)
        compose.waitForIdle()
    }

    private fun plan(id: String) = WorkoutTemplate(
        id = id,
        name = id,
        source = PlanSource.MANUAL,
        exercises = listOf(
            PlannedExercise(
                id = "$id-0",
                exerciseId = ExerciseId("0025"),
                position = 0,
                target = ExerciseTarget.Reps(sets = 3, reps = 10),
                restMs = 60_000L,
            ),
        ),
    )

    private companion object {
        // Distinct enough that a plan left behind by another test class cannot
        // be mistaken for one of these.
        const val RUNNING = "Conflict test running plan"
        const val REQUESTED = "Conflict test requested plan"
    }
}

/**
 * Taps the Start button belonging to one named plan.
 *
 * By ancestor rather than by index. Every card has a button reading "Start",
 * and the list contains whatever previous tests saved — so an index would pick
 * a different plan the moment anything else is in the database, and would do it
 * silently.
 */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.startPlan(name: String) {
    onAllNodesWithText(AppText.startPlan)
        .filterToOne(hasAnyAncestor(hasText(name, substring = true)))
        .performClick()
    waitForIdle()
}
