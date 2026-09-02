package com.repforth.feature.builder

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.repforth.core.designsystem.theme.RepForthPreviewHost
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MediaRef
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WeekDay
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.testing.ENGLISH
import com.repforth.core.testing.SCREENSHOT_DEVICE
import com.repforth.core.testing.SCREENSHOT_SDK
import com.repforth.core.testing.TURKISH
import com.repforth.core.testing.screenshotPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three surfaces of the builder that hold text of unpredictable length.
 *
 * Plans is here because a week's day rows carry a name the model wrote, under a
 * "Day N" the app wrote, beside two icon buttons — and getting that combination
 * wrong is what produced "Day 1: Day 1: Chest". Week review is here because a
 * generated day is a list of catalog names, which are long, next to three
 * controls that must stay tappable. Coach is here because it grew from one
 * control to four.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = SCREENSHOT_DEVICE)
class BuilderScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun plans_english() = capture("plans-en") { PlansContent() }

    @Test
    fun plans_turkish() = capture("plans-tr", locale = TURKISH) { PlansContent() }

    @Test
    fun plans_large_text() = capture("plans-en-2x", fontScale = 2f) { PlansContent() }

    @Test
    fun plans_turkish_large_text() =
        capture("plans-tr-2x", locale = TURKISH, fontScale = 2f) { PlansContent() }

    @Test
    fun week_review_english() = capture("week-review-en") { WeekReviewContent() }

    @Test
    fun week_review_turkish() = capture("week-review-tr", locale = TURKISH) { WeekReviewContent() }

    @Test
    fun week_review_large_text() =
        capture("week-review-en-2x", fontScale = 2f) { WeekReviewContent() }

    /** The empty builder, which is where someone decides how to start. */
    @Test
    fun builder_empty_english() = capture("builder-empty-en") { BuilderContent(EMPTY) }

    @Test
    fun builder_empty_turkish() =
        capture("builder-empty-tr", locale = TURKISH) { BuilderContent(EMPTY) }

    @Test
    fun builder_empty_large_text() =
        capture("builder-empty-en-2x", fontScale = 2f) { BuilderContent(EMPTY) }

    @Test
    fun builder_with_rows() = capture("builder-rows-en") { BuilderContent(WITH_ROWS) }

    @Test
    fun coach_english() = capture("coach-en") { CoachContent() }

    @Test
    fun coach_turkish() = capture("coach-tr", locale = TURKISH) { CoachContent() }

    @Test
    fun coach_large_text() = capture("coach-en-2x", fontScale = 2f) { CoachContent() }

    @Test
    fun coach_turkish_large_text() =
        capture("coach-tr-2x", locale = TURKISH, fontScale = 2f) { CoachContent() }

    private fun capture(
        name: String,
        locale: String? = null,
        fontScale: Float = 1f,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        // Both, always, even at their defaults. Robolectric carries
        // qualifiers across test methods in the same JVM, so a test that set
        // only what it changed rendered in whatever the previous one left
        // behind -- English goldens came out Turkish when the suite ran in a
        // different order, and passed when the class ran alone.
        RuntimeEnvironment.setQualifiers("+${locale ?: ENGLISH}")
        RuntimeEnvironment.setFontScale(fontScale)

        compose.setContent { RepForthPreviewHost { content() } }
        compose.onRoot().captureRoboImage(screenshotPath(name))
    }

    @androidx.compose.runtime.Composable
    private fun PlansContent() {
        PlansScreen(
            plans = listOf(STANDALONE),
            weeklyPlans = listOf(WEEK),
            onNewWorkout = {},
            onEditPlan = {},
            onEditWeek = {},
            onStartPlan = {},
            onDelete = {},
        )
    }

    @androidx.compose.runtime.Composable
    private fun BuilderContent(state: BuilderUiState) {
        BuilderScreen(
            state = state,
            onNameChange = {},
            onAddExercise = {},
            onOpenExerciseDetail = {},
            onCoach = {},
            onRemove = {},
            onMove = { _, _ -> },
            onSetsChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onDurationChange = { _, _ -> },
            onRestChange = { _, _ -> },
            onWeightChange = { _, _ -> },
            onTimedChange = { _, _ -> },
            onSave = {},
        )
    }

    @androidx.compose.runtime.Composable
    private fun CoachContent() {
        CoachScreen(
            state = COACH,
            onMuscleToggled = {},
            onRegionToggled = {},
            onGenerate = {},
            onDaysChange = {},
            onGoalChange = {},
            onExperienceChange = {},
            onSessionMinutesChange = {},
            onSaveDefaults = {},
            onCancelGenerate = {},
            onDismissError = {},
            onClose = {},
        )
    }

    @androidx.compose.runtime.Composable
    private fun WeekReviewContent() {
        WeekReviewScreen(
            state = WEEK_DRAFT,
            onWeekNameChange = {},
            onDayTitleChange = { _, _ -> },
            onToggleDayExpanded = {},
            onAddExerciseToDay = {},
            onOpenExerciseDetail = {},
            onRemoveExerciseFromDay = { _, _ -> },
            onMoveExerciseInDay = { _, _, _ -> },
            onSetsChangeInDay = { _, _, _ -> },
            onRepsChangeInDay = { _, _, _ -> },
            onDurationChangeInDay = { _, _, _ -> },
            onRestChangeInDay = { _, _, _ -> },
            onWeightChangeInDay = { _, _, _ -> },
            onTimedChangeInDay = { _, _, _ -> },
            onSaveWeek = {},
            onCoach = {},
        )
    }

    private companion object {
        fun template(id: String, name: String) = WorkoutTemplate(
            id = id,
            name = name,
            source = PlanSource.AI,
            exercises = List(5) { index ->
                PlannedExercise(
                    id = "$id-$index",
                    exerciseId = ExerciseId("ex-$index"),
                    position = index,
                    target = ExerciseTarget.Reps(sets = 3, reps = 10),
                    restMs = 60_000L,
                )
            },
        )

        val STANDALONE = template("standalone", "Quick full body")

        /**
         * Day titles as a model actually writes them.
         *
         * One of them still carries the "Day 1:" prefix the app also renders, so
         * the picture shows what `weekDayLabel` does about it rather than only
         * asserting it in a unit test.
         */
        val WEEK = TrainingWeek(
            id = "w1",
            name = "Weekly program",
            source = PlanSource.AI,
            active = true,
            days = listOf(
                WeekDay(0, "Day 1: Chest and triceps", workout = template("d0", "Chest")),
                WeekDay(1, "Back and biceps", workout = template("d1", "Back")),
                WeekDay(2, "Legs", workout = template("d2", "Legs")),
            ),
        )

        /** A real catalog name, which is the length these rows have to survive. */
        val LONG_EXERCISE = DraftExercise(
            id = "row-0",
            exerciseId = ExerciseId("0025"),
            name = "barbell decline wide-grip press",
            thumbnail = MediaRef.Unavailable,
            sets = 4,
            reps = 12,
            durationSeconds = 30,
            weightKg = 60.0,
            restSeconds = 90,
            timed = false,
        )

        val PROFILE = UserProfile(
            id = "screenshot",
            goal = TrainingGoal.GENERAL_FITNESS,
            experience = ExperienceLevel.ADVANCED,
            trainingDaysPerWeek = 3,
            sessionLengthMs = 45 * 60_000L,
            availableEquipment = setOf(Equipment.BARBELL),
            preferredMuscles = emptySet(),
            exclusions = emptySet(),
        )

        /**
         * Coach with its shape controls showing, and one of them changed.
         *
         * A changed value is the interesting state: it is the only one where
         * "Save as default" is enabled, and an enabled button is what the
         * picture is for.
         */
        val COACH = BuilderUiState(
            coaching = true,
            coachDays = 6,
            coachGoal = TrainingGoal.ENDURANCE,
            coachExperience = ExperienceLevel.ADVANCED,
            coachSessionMinutes = 60,
            savedProfile = PROFILE,
        )

        /**
         * A new plan with nothing in it.
         *
         * The state that decides what someone does next, and the reason Coach
         * sits above "Add exercise" as a filled button rather than beside it as
         * an equal.
         */
        val EMPTY = BuilderUiState(sessionCeilingMinutes = 45)

        val WITH_ROWS = BuilderUiState(
            name = "Push day",
            sessionCeilingMinutes = 45,
            exercises = listOf(LONG_EXERCISE, LONG_EXERCISE.copy(id = "row-9")),
        )

        val WEEK_DRAFT = BuilderUiState(
            name = "Weekly program",
            source = PlanSource.AI,
            sessionCeilingMinutes = 45,
            weekDays = listOf(
                DraftWeekDay(
                    dayIndex = 0,
                    title = "Chest and triceps",
                    exercises = listOf(LONG_EXERCISE),
                    isExpanded = true,
                ),
                DraftWeekDay(
                    dayIndex = 1,
                    title = "Back and biceps",
                    exercises = listOf(LONG_EXERCISE.copy(id = "row-1")),
                    isExpanded = false,
                ),
            ),
            savedProfile = PROFILE,
        )
    }
}
