package com.repforth.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_COMPARISON
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.screenshotPath
import com.repforth.core.workout.ProgressSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Today, following a week and not following one.
 *
 * The week label is the thing worth a picture. It sits under a workout name of
 * unknown length, beside a day count, and reads "Weekly program · Day 1 of 7" —
 * in Turkish, at 200% font scale, on a phone. Nothing about that combination had
 * been looked at, and the last defect on this screen was a number that had been
 * wrong for as long as weeks existed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class TodayScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun today_following_a_week() = capture("today-week-en", FOLLOWING_A_WEEK)

    @Test
    fun today_following_a_week_turkish() =
        capture("today-week-tr", FOLLOWING_A_WEEK, locale = TURKISH)

    @Test
    fun today_following_a_week_large_text() =
        capture("today-week-en-2x", FOLLOWING_A_WEEK, fontScale = 2f)

    @Test
    fun today_following_a_week_turkish_large_text() =
        capture("today-week-tr-2x", FOLLOWING_A_WEEK, locale = TURKISH, fontScale = 2f)

    /** A standalone plan carries no week label, and the card has to look right without one. */
    @Test
    fun today_standalone_plan() = capture("today-standalone-en", STANDALONE)

    @Test
    fun today_nothing_saved() = capture("today-empty-en", TodayUiState(loading = false))

    private fun capture(
        name: String,
        state: TodayUiState,
        locale: String? = null,
        fontScale: Float = 1f,
    ) {
        // Both, always, even at their defaults. Robolectric carries
        // qualifiers across test methods in the same JVM, so a test that set
        // only what it changed rendered in whatever the previous one left
        // behind -- English goldens came out Turkish when the suite ran in a
        // different order, and passed when the class ran alone.
        RuntimeEnvironment.setQualifiers("+${locale ?: ENGLISH}")
        RuntimeEnvironment.setFontScale(fontScale)

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

        compose.onRoot().captureRoboImage(screenshotPath(name), SCREENSHOT_COMPARISON)
    }

    private companion object {
        val PLAN = WorkoutTemplate(
            id = "d0",
            // Long on purpose: a short name proves nothing about a card that has
            // to hold a week label and a day count underneath it.
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
            // Seven against a profile that says three: the case that used to
            // render "2 of 3 days" while following a seven-day week.
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
