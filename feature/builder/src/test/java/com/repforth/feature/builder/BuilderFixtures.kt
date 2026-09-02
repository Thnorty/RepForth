package com.repforth.feature.builder

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

/**
 * The states the builder's four screens are rendered in, for every test that
 * renders them.
 *
 * They live in one place because two suites now want the same ones and a second
 * copy would drift: the screenshot tests look at these states, and the
 * accessibility tests walk the semantics of the very same trees. A fixture that
 * differed between them would mean the picture and the screen-reader check were
 * describing different screens.
 */
internal object BuilderFixtures {

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
