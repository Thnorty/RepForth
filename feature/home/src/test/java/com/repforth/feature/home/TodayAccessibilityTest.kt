package com.repforth.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.assertScreenIsAccessible
import com.repforth.core.workout.ProgressSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What TalkBack would find on Today, the first screen after launch. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class TodayAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun following_a_week_english() = check(ENGLISH, FOLLOWING_A_WEEK)

    @Test
    fun following_a_week_turkish() = check(TURKISH, FOLLOWING_A_WEEK)

    @Test
    fun standalone_english() = check(ENGLISH, STANDALONE)

    /** Nothing planned yet is what a new install shows first. */
    @Test
    fun empty_english() = check(ENGLISH, TodayUiState(loading = false))

    private fun check(locale: String, state: TodayUiState) {
        RuntimeEnvironment.setQualifiers("+$locale")
        RuntimeEnvironment.setFontScale(1f)

        compose.setContent {
            RepForthPreviewHost {
                TodayScreen(
                    state = state,
                    onResumeWorkout = {},
                    onStartPlan = {},
                    onBuildWorkout = {},
                )
            }
        }

        compose.assertScreenIsAccessible("Today ($locale)")
    }

    private companion object {
        val PLAN = WorkoutTemplate(
            id = "d0",
            name = "Upper body push and accessories",
            source = PlanSource.AI,
            exercises = List(6) { index ->
                PlannedExercise(
                    id = "row-$index",
                    exerciseId = ExerciseId("ex-$index"),
                    position = index,
                    target = ExerciseTarget.Reps(sets = 3, reps = 10),
                    restMs = 60_000L,
                )
            },
        )

        val FOLLOWING_A_WEEK = TodayUiState(
            next = PLAN,
            progress = ProgressSummary(workoutsThisWeek = 2, daysThisWeek = 2, streakWeeks = 3),
            trainingDaysPerWeek = 3,
            activeWeekName = "Weekly program",
            nextWeekDayPosition = 0,
            activeWeekDayCount = 7,
            loading = false,
        )

        val STANDALONE = TodayUiState(
            next = PLAN,
            progress = ProgressSummary(workoutsThisWeek = 1, daysThisWeek = 1),
            trainingDaysPerWeek = 3,
            loading = false,
        )
    }
}
