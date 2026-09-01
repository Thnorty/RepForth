package com.repforth.feature.builder

import com.repforth.core.ai.AI_WORKOUT_SCHEMA_VERSION
import com.repforth.core.ai.AiGenerationFailureReason
import com.repforth.core.ai.AiPlannedDay
import com.repforth.core.ai.AiPlannedExercise
import com.repforth.core.ai.AiWorkoutGenerationOutcome
import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutResponse
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.allMuscles
import com.repforth.core.model.synonyms
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.Language
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.model.TrainingWeek
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.WeekRepository
import com.repforth.core.rules.GenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The builder's rules, without a database or a device.
 *
 * The one that matters most is renumbering. [WorkoutTemplate] refuses positions
 * that are not contiguous and in order, so a plan whose rows were moved and then
 * saved without renumbering does not fail validation — it throws on
 * construction, inside a coroutine, which is a crash rather than a message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuilderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var templates: RecordingTemplateRepository
    private lateinit var weeks: RecordingWeekRepository
    private lateinit var catalog: FakeExercises
    private lateinit var profiles: FakeProfiles
    private lateinit var generator: FakeWorkoutGenerator
    private lateinit var viewModel: BuilderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        templates = RecordingTemplateRepository()
        weeks = RecordingWeekRepository()
        catalog = FakeExercises()
        profiles = FakeProfiles()
        generator = FakeWorkoutGenerator()
        viewModel = BuilderViewModel(templates, catalog, profiles, generator, weeks)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    // ---- Coach (§3, §8): the rules engine reaching the builder ----

    private fun candidate(
        id: String,
        muscle: Muscle,
        equipment: Equipment = Equipment.BODY_WEIGHT,
        timed: Boolean = false,
    ) =
        ExerciseCandidate(
            id = ExerciseId(id),
            name = "Exercise $id",
            bodyPart = if (timed) BodyPart.CARDIO else BodyPart.CHEST,
            target = muscle,
            muscleGroup = muscle,
            secondaryMuscles = emptySet(),
            equipment = equipment,
        )

    @Test
    fun `missing provider configuration leaves the builder unchanged and explains setup`() =
        runTest(dispatcher) {
            catalog.catalog = listOf(candidate("a", Muscle.PECTORALS), candidate("b", Muscle.LATS))
            viewModel.onCoachOpen()

            viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
            advanceUntilIdle()

            assertTrue("No local substitute is built", state.exercises.isEmpty())
            assertEquals(R.string.coach_error_no_config_title, state.coachError?.titleRes)
            assertTrue("Coach stays open so setup can be corrected", state.coaching)
            assertFalse(state.generating)
        }

    @Test
    fun `a provider answer keeps its exact targets rationale locale and source`() =
        runTest(dispatcher) {
            catalog.catalog = listOf(
                candidate("a", Muscle.PECTORALS),
                candidate("b", Muscle.LATS, timed = true),
            )
            generator.response = AiWorkoutResponse(
                schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
                days = listOf(
                    AiPlannedDay(
                        dayIndex = 0,
                        title = "Push",
                        exercises = listOf(
                            AiPlannedExercise(
                                exerciseId = "a",
                                order = 0,
                                sets = 4,
                                repetitions = 12,
                                restSeconds = 75,
                            ),
                            AiPlannedExercise(
                                exerciseId = "b",
                                order = 1,
                                sets = 3,
                                durationSeconds = 45,
                                restSeconds = 30,
                            ),
                        ),
                    ),
                ),
                rationale = "Dengeli hacim",
            )

            viewModel.onGenerate("Koç planı", DAY_TITLES, Language.TURKISH)
            advanceUntilIdle()

            assertEquals(Language.TURKISH, generator.locales.single())
            assertEquals(PlanSource.AI, state.source)
            assertEquals(12, state.exercises[0].reps)
            assertFalse(state.exercises[0].timed)
            assertEquals(45, state.exercises[1].durationSeconds)
            assertTrue(state.exercises[1].timed)
            assertEquals("Dengeli hacim", state.coachNotice?.rationale)

            viewModel.onSave()
            advanceUntilIdle()

            assertEquals(PlanSource.AI, templates.saved.single().source)
            assertEquals(12, (templates.saved.single().exercises[0].target as ExerciseTarget.Reps).reps)
        }

    @Test
    fun `provider failure shows error dialog and leaves the request ready to retry`() =
        runTest(dispatcher) {
            catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))
            generator.failure = ProviderFailure.NETWORK
            viewModel.onCoachMuscleToggled(Muscle.PECTORALS)

            viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
            advanceUntilIdle()

            val error = state.coachError
            assertEquals(R.string.coach_error_network_title, error?.titleRes)
            assertEquals(R.string.coach_error_network_body, error?.messageRes)
            assertTrue(error?.canRetry == true)
            assertTrue("No local plan is generated on provider failure", state.exercises.isEmpty())
            assertEquals(Muscle.PECTORALS.synonyms, state.coachMuscles)

            viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
            advanceUntilIdle()

            assertEquals(2, generator.locales.size)
            assertEquals(Muscle.PECTORALS.synonyms, state.coachMuscles)
        }

    @Test
    fun `timeout failure shows timeout dialog with retry enabled`() =
        runTest(dispatcher) {
            catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))
            generator.failure = ProviderFailure.TIMEOUT
            viewModel.onCoachMuscleToggled(Muscle.PECTORALS)

            viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
            advanceUntilIdle()

            val error = state.coachError
            assertEquals(R.string.coach_error_timeout_title, error?.titleRes)
            assertEquals(R.string.coach_error_timeout_body, error?.messageRes)
            assertTrue(error?.canRetry == true)
            assertTrue("No local plan is generated on timeout", state.exercises.isEmpty())
        }

    /**
     * The whole reason Coach lives inside the builder: nothing is written until
     * the user says so, and every number stays editable first.
     */
    @Test
    fun `generating saves nothing on its own`() = runTest(dispatcher) {
        catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))

        viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertTrue("Nothing was persisted", templates.saved.isEmpty())
    }

    @Test
    fun `a name the user typed survives generation`() = runTest(dispatcher) {
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(
                AiPlannedDay(
                    dayIndex = 0,
                    title = "",
                    exercises = listOf(
                        AiPlannedExercise(
                            exerciseId = "a",
                            order = 0,
                            sets = 3,
                            repetitions = 10,
                            restSeconds = 60,
                        ),
                    ),
                ),
            ),
            rationale = "Rationale",
        )
        catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))
        viewModel.onNameChange("Leg day")

        viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertEquals("Leg day", state.name)
    }

    @Test
    fun `an empty name takes the default`() = runTest(dispatcher) {
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(
                AiPlannedDay(
                    dayIndex = 0,
                    title = "",
                    exercises = listOf(
                        AiPlannedExercise(
                            exerciseId = "a",
                            order = 0,
                            sets = 3,
                            repetitions = 10,
                            restSeconds = 60,
                        ),
                    ),
                ),
            ),
            rationale = "Rationale",
        )
        catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))

        viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertEquals("Coach plan", state.name)
    }

    @Test
    fun `no profile is reported rather than generating from nothing`() = runTest(dispatcher) {
        profiles.profile = null
        catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))

        viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertEquals(CoachFailure.NO_PROFILE, state.coachFailure)
        assertTrue("Nothing was generated from a profile that does not exist", state.exercises.isEmpty())
        assertFalse(state.generating)
    }

    @Test
    fun `an empty catalog does not crash`() = runTest(dispatcher) {
        catalog.catalog = emptyList()

        viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertEquals(R.string.coach_error_no_candidates_title, state.coachError?.titleRes)
        assertTrue(state.exercises.isEmpty())
    }

    /**
     * A muscle the dataset names twice must be selected under both names, or the
     * engine honours half the request and silently skips the rest.
     */
    @Test
    fun `toggling a muscle takes its synonyms with it`() {
        viewModel.onCoachMuscleToggled(Muscle.PECTORALS)

        assertEquals(Muscle.PECTORALS.synonyms, state.coachMuscles)

        viewModel.onCoachMuscleToggled(Muscle.PECTORALS)

        assertTrue("Toggling off clears the whole group", state.coachMuscles.isEmpty())
    }

    @Test
    fun `a region selects every muscle in it`() {
        viewModel.onCoachRegionToggled(BodyRegion.entries.first())

        assertEquals(BodyRegion.entries.first().allMuscles(), state.coachMuscles)
    }


    private fun addExercises(count: Int) {
        repeat(count) { index ->
            viewModel.onExerciseAdded(ExerciseId("ex-$index"), "Exercise $index")
        }
    }

    @Test
    fun `a plan needs a name and at least one exercise`() {
        assertFalse(state.canSave)

        viewModel.onNameChange("Push day")
        assertFalse("A named plan with nothing in it is not a plan", state.canSave)

        addExercises(1)
        assertTrue(state.canSave)

        viewModel.onNameChange("   ")
        assertFalse("Whitespace is not a name", state.canSave)
    }

    @Test
    fun `saving writes positions contiguous from zero, in order`() = runTest(dispatcher) {
        viewModel.onNameChange("Push day")
        addExercises(3)

        viewModel.onSave()
        testScheduler.advanceUntilIdle()

        val saved = templates.saved.single()
        assertEquals(listOf(0, 1, 2), saved.exercises.map { it.position })
    }

    /**
     * Moving a row and saving is the case that would otherwise throw:
     * WorkoutTemplate's constructor requires positions to equal their indices,
     * so a reorder without renumbering is a crash and not a validation message.
     */
    @Test
    fun `reordering renumbers rather than leaving a gap`() = runTest(dispatcher) {
        viewModel.onNameChange("Push day")
        addExercises(3)

        viewModel.onMove(0, 2)
        viewModel.onSave()
        testScheduler.advanceUntilIdle()

        val saved = templates.saved.single()
        assertEquals(listOf(0, 1, 2), saved.exercises.map { it.position })
        assertEquals(
            "The moved row should now be last",
            "Exercise 0",
            state.exercises.last().name,
        )
    }

    @Test
    fun `moving outside the list does nothing`() {
        addExercises(2)
        val before = state.exercises.map { it.id }

        viewModel.onMove(0, -1)
        viewModel.onMove(1, 5)
        viewModel.onMove(9, 0)

        assertEquals(before, state.exercises.map { it.id })
    }

    @Test
    fun `removing takes out the row that was asked for`() {
        addExercises(3)

        viewModel.onRemove(1)

        assertEquals(listOf("Exercise 0", "Exercise 2"), state.exercises.map { it.name })
    }

    @Test
    fun `numeric edits are clamped to their declared ranges`() {
        addExercises(1)

        viewModel.onSetsChange(0, 999)
        assertEquals(BuilderViewModel.SETS_RANGE.last, state.exercises[0].sets)

        viewModel.onSetsChange(0, 0)
        assertEquals(BuilderViewModel.SETS_RANGE.first, state.exercises[0].sets)

        viewModel.onRestChange(0, -30)
        assertEquals(0, state.exercises[0].restSeconds)
    }

    /**
     * A plank has a duration and a curl has reps; nothing has both. Switching
     * keeps the other value in the draft so that changing your mind twice does
     * not lose what was typed the first time.
     */
    @Test
    fun `switching between reps and time keeps both values in the draft`() {
        addExercises(1)
        viewModel.onRepsChange(0, 12)
        viewModel.onDurationChange(0, 45)

        viewModel.onTimedChange(0, true)
        assertTrue(state.exercises[0].target is ExerciseTarget.Duration)
        assertEquals(45_000L, (state.exercises[0].target as ExerciseTarget.Duration).durationMs)

        viewModel.onTimedChange(0, false)
        assertEquals(12, (state.exercises[0].target as ExerciseTarget.Reps).reps)
    }

    @Test
    fun `a blank weight clears it rather than storing zero`() {
        addExercises(1)

        viewModel.onWeightChange(0, 60.0)
        assertEquals(60.0, state.exercises[0].weightKg!!, 0.001)

        viewModel.onWeightChange(0, null)
        assertEquals(null, state.exercises[0].weightKg)
    }

    @Test
    fun `saving twice writes one plan`() = runTest(dispatcher) {
        viewModel.onNameChange("Push day")
        addExercises(1)

        viewModel.onSave()
        viewModel.onSave()
        testScheduler.advanceUntilIdle()

        assertEquals(1, templates.saved.size)
    }

    @Test
    fun `editing a saved plan writes back to the same id`() = runTest(dispatcher) {
        val existing = WorkoutTemplate(
            id = "plan-1",
            name = "Leg day",
            source = com.repforth.core.model.PlanSource.MANUAL,
            exercises = listOf(
                com.repforth.core.model.PlannedExercise(
                    id = "row-1",
                    exerciseId = ExerciseId("ex-0"),
                    position = 0,
                    target = ExerciseTarget.Reps(3, 10),
                    restMs = 90_000L,
                ),
            ),
        )
        templates.saved += existing

        viewModel.load("plan-1")
        testScheduler.advanceUntilIdle()

        assertEquals("Leg day", state.name)
        assertEquals("Exercise 0", state.exercises.single().name)

        viewModel.onSave()
        testScheduler.advanceUntilIdle()

        assertEquals("plan-1", templates.saved.last().id)
    }

    /**
     * A dataset update can remove an exercise a saved plan still references.
     * Keeping the id as the name is ugly and honest; dropping the row would let
     * someone save a shorter plan than the one they opened without being told.
     */
    @Test
    fun `a row whose exercise left the catalog keeps its place`() = runTest(dispatcher) {
        templates.saved += WorkoutTemplate(
            id = "plan-2",
            name = "Old plan",
            source = com.repforth.core.model.PlanSource.MANUAL,
            exercises = listOf(
                com.repforth.core.model.PlannedExercise(
                    id = "row-1",
                    exerciseId = ExerciseId("vanished"),
                    position = 0,
                    target = ExerciseTarget.Reps(3, 10),
                    restMs = 60_000L,
                ),
            ),
        )

        viewModel.load("plan-2")
        testScheduler.advanceUntilIdle()

        assertEquals(1, state.exercises.size)
        assertEquals("vanished", state.exercises.single().name)
    }

    @Test
    fun `the estimate is compared against the session length from onboarding`() = runTest(dispatcher) {
        testScheduler.advanceUntilIdle()
        assertEquals(FAKE_CEILING_MINUTES, state.sessionCeilingMinutes)

        addExercises(1)
        assertFalse("One exercise should fit inside the ceiling", state.exceedsCeiling)

        addExercises(20)
        assertTrue("Twenty-one exercises should not", state.exceedsCeiling)
    }

    @Test
    fun `coach generation creates multi-day draft and enters week review`() = runTest(dispatcher) {
        val day0 = AiPlannedDay(
            dayIndex = 0,
            title = "Upper Push",
            exercises = listOf(
                AiPlannedExercise("a", 0, sets = 3, repetitions = 10, restSeconds = 60),
            ),
        )
        val day1 = AiPlannedDay(
            dayIndex = 1,
            title = "Lower & Core",
            exercises = listOf(
                AiPlannedExercise("b", 0, sets = 3, repetitions = 12, restSeconds = 60),
            ),
        )
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(day0, day1),
            rationale = "Two-day split",
        )
        catalog.catalog = listOf(
            candidate("a", Muscle.PECTORALS),
            candidate("b", Muscle.QUADRICEPS),
        )

        viewModel.onGenerate("Weekly Program", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertTrue(state.isWeeklyPlan)
        assertEquals(2, state.weekDays.size)
        assertEquals("Upper Push", state.weekDays[0].title)
        assertEquals("Lower & Core", state.weekDays[1].title)
        assertEquals(1, state.weekDays[0].exercises.size)
        assertEquals(1, state.weekDays[1].exercises.size)
        assertTrue(state.weekDays[0].isExpanded)
        assertFalse(state.weekDays[1].isExpanded)
        assertEquals("Two-day split", state.coachNotice?.rationale)
    }

    @Test
    fun `a multi-day answer is saved as a week`() = runTest(dispatcher) {
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(day(0, "Push", "a"), day(1, "Pull", "b")),
            rationale = "Upper/lower",
        )
        catalog.catalog = listOf(
            candidate("a", Muscle.PECTORALS),
            candidate("b", Muscle.LATS),
        )

        viewModel.onCoachDaysChange(2)
        viewModel.onGenerate("My Week", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertTrue(state.isWeeklyPlan)
        assertTrue(state.canSaveWeek)
        viewModel.onSaveWeek("My Week", DAY_TITLES)
        advanceUntilIdle()

        assertTrue(state.saved)
        assertEquals(1, weeks.saved.size)
        assertEquals("My Week", weeks.saved.single().name)
        assertEquals(2, weeks.saved.single().days.size)
    }

    /**
     * One day is a workout, not a week of one.
     *
     * The wire contract always speaks in days so that there is a single schema
     * and a single validator, but a one-day answer must not become a week card
     * in Plans wrapping a single workout.
     */
    @Test
    fun `a one-day answer is saved as a standalone workout, not a week`() = runTest(dispatcher) {
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(day(0, "Day 1", "a")),
            rationale = "One-day full body",
        )
        catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))

        viewModel.onCoachDaysChange(1)
        viewModel.onGenerate("Chest day", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertFalse("A single day must not open the week review", state.isWeeklyPlan)
        assertTrue(state.weekDays.isEmpty())
        assertEquals(1, state.exercises.size)
        assertTrue(state.canSave)

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("It belongs in the plan library, not the week library", 1, templates.saved.size)
        assertTrue("No week may be written for a single workout", weeks.saved.isEmpty())
    }

    /**
     * A second week does not silently take over what Today offers.
     *
     * The first week has nothing to displace, so it becomes active. A later one
     * would otherwise change what the app tells you to train today without
     * asking; Plans has an explicit "set active" action for that.
     */
    @Test
    fun `a generated week only becomes active when no week is active yet`() = runTest(dispatcher) {
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(day(0, "Push", "a"), day(1, "Pull", "b")),
            rationale = "Upper/lower",
        )
        catalog.catalog = listOf(
            candidate("a", Muscle.PECTORALS),
            candidate("b", Muscle.LATS),
        )

        viewModel.onCoachDaysChange(2)
        viewModel.onGenerate("First", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()
        viewModel.onSaveWeek("First", DAY_TITLES)
        advanceUntilIdle()

        assertTrue("The first week has nothing to displace", weeks.saved.single().active)

        weeks.activeWeek = weeks.saved.single()
        viewModel.onGenerate("Second", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()
        viewModel.onSaveWeek("Second", DAY_TITLES)
        advanceUntilIdle()

        assertFalse(
            "A later week must not displace the active one without being asked",
            weeks.saved.last().active,
        )
    }

    /**
     * Re-saving a week keeps each day's template id.
     *
     * History records the template a session was performed from, and Today picks
     * the next day by matching those ids. Minting fresh ids on every save would
     * silently reset "which day have I not done yet".
     */
    @Test
    fun `re-saving a week keeps the same template id for each day`() = runTest(dispatcher) {
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(day(0, "Push", "a"), day(1, "Pull", "b")),
            rationale = "Upper/lower",
        )
        catalog.catalog = listOf(
            candidate("a", Muscle.PECTORALS),
            candidate("b", Muscle.LATS),
        )

        viewModel.onCoachDaysChange(2)
        viewModel.onGenerate("My Week", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        viewModel.onSaveWeek("My Week", DAY_TITLES)
        advanceUntilIdle()
        val firstIds = weeks.saved.last().days.map { it.workout.id }

        viewModel.onDayTitleChange(0, "Push A")
        viewModel.onSaveWeek("My Week", DAY_TITLES)
        advanceUntilIdle()

        assertEquals(
            "Editing and re-saving must not renumber the days' template ids",
            firstIds,
            weeks.saved.last().days.map { it.workout.id },
        )
    }

    @Test
    fun `day operations manipulate targeted day independently`() = runTest(dispatcher) {
        val day0 = AiPlannedDay(
            dayIndex = 0,
            title = "Day 1",
            exercises = listOf(
                AiPlannedExercise("a", 0, sets = 3, repetitions = 10, restSeconds = 60),
                AiPlannedExercise("b", 1, sets = 3, repetitions = 10, restSeconds = 60),
            ),
        )
        val day1 = AiPlannedDay(
            dayIndex = 1,
            title = "Day 2",
            exercises = listOf(
                AiPlannedExercise("c", 0, sets = 3, repetitions = 10, restSeconds = 60),
            ),
        )
        generator.response = AiWorkoutResponse(
            schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
            days = listOf(day0, day1),
            rationale = "Split",
        )
        catalog.catalog = listOf(
            candidate("a", Muscle.PECTORALS),
            candidate("b", Muscle.LATS),
            candidate("c", Muscle.QUADRICEPS),
        )

        viewModel.onGenerate("Split Program", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        // Toggle expansion
        viewModel.onToggleDayExpanded(1)
        assertTrue(state.weekDays[1].isExpanded)

        // Change title
        viewModel.onDayTitleChange(0, "Push Focus")
        assertEquals("Push Focus", state.weekDays[0].title)

        // Move exercise in day 0
        viewModel.onMove(0, 1, 0)
        assertEquals("b", state.weekDays[0].exercises[0].exerciseId.value)
        assertEquals("a", state.weekDays[0].exercises[1].exerciseId.value)

        // Remove exercise in day 0
        viewModel.onRemove(0, 0)
        assertEquals(1, state.weekDays[0].exercises.size)
        assertEquals("a", state.weekDays[0].exercises[0].exerciseId.value)

        // Day 1 unchanged
        assertEquals(1, state.weekDays[1].exercises.size)
        assertEquals("c", state.weekDays[1].exercises[0].exerciseId.value)
    }
}

private const val FAKE_CEILING_MINUTES = 45

private fun day(index: Int, title: String, vararg exerciseIds: String) = AiPlannedDay(
    dayIndex = index,
    title = title,
    exercises = exerciseIds.mapIndexed { i, id ->
        AiPlannedExercise(id, i, sets = 3, repetitions = 10, restSeconds = 60)
    },
)

private class RecordingWeekRepository : WeekRepository {
    val saved = mutableListOf<TrainingWeek>()
    var activeId: String? = null
    private val all = MutableStateFlow<List<TrainingWeek>>(emptyList())
    private val active = MutableStateFlow<TrainingWeek?>(null)

    override fun observeAll(): Flow<List<TrainingWeek>> = all
    override fun observeActive(): Flow<TrainingWeek?> = active
    override suspend fun find(id: String): TrainingWeek? = saved.firstOrNull { it.id == id }
    override suspend fun save(week: TrainingWeek) {
        saved.removeAll { it.id == week.id }
        saved += week
        all.value = saved.toList()
    }
    override suspend fun setActive(id: String) {
        activeId = id
        active.value = saved.firstOrNull { it.id == id }
    }

    /** Lets a test say "a week is already active" without going through save. */
    var activeWeek: TrainingWeek?
        get() = active.value
        set(value) {
            active.value = value
        }
    override suspend fun delete(id: String) {
        saved.removeAll { it.id == id }
        all.value = saved.toList()
    }
    override suspend fun deleteAll() {
        saved.clear()
        all.value = emptyList()
        active.value = null
    }
}

private class RecordingTemplateRepository : TemplateRepository {
    val saved = mutableListOf<WorkoutTemplate>()
    private val all = MutableStateFlow<List<WorkoutTemplate>>(emptyList())

    override fun observeAll(): Flow<List<WorkoutTemplate>> = all

    override suspend fun find(id: String): WorkoutTemplate? = saved.firstOrNull { it.id == id }

    override suspend fun save(template: WorkoutTemplate) {
        saved.removeAll { it.id == template.id }
        saved += template
        all.value = saved.toList()
    }

    override suspend fun delete(id: String) {
        saved.removeAll { it.id == id }
    }

    override suspend fun deleteAll() = saved.clear()
}

private class FakeExercises : ExerciseRepository {
    var catalog: List<ExerciseCandidate> = emptyList()

    override suspend fun candidates(): List<ExerciseCandidate> = catalog

    override suspend fun count(): Int = 0

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> = emptyFlow()

    override suspend fun find(id: ExerciseId): Exercise? = null

    override suspend fun summaries(
        ids: Collection<ExerciseId>,
    ): Map<ExerciseId, ExerciseSummary> = ids
        .filter { it.value != "vanished" }
        .associateWith { id ->
            ExerciseSummary(
                id = id,
                name = "Exercise " + id.value.removePrefix("ex-"),
                bodyPart = BodyPart.entries.first(),
                target = Muscle.entries.first(),
                equipment = Equipment.BODY_WEIGHT,
            )
        }
}

private class FakeWorkoutGenerator : AiWorkoutGenerationService {
    var response: AiWorkoutResponse? = null
    var failure: ProviderFailure? = null
    val locales = mutableListOf<Language>()

    override suspend fun generate(
        request: GenerationRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiWorkoutGenerationOutcome {
        locales += locale
        response?.let { return AiWorkoutGenerationOutcome.Provider(it, attempts = 1) }
        return AiWorkoutGenerationOutcome.Failure(
            reason = if (failure == null) {
                if (candidates.isEmpty()) {
                    AiGenerationFailureReason.NO_ELIGIBLE_CANDIDATES
                } else {
                    AiGenerationFailureReason.NO_PROVIDER_CONFIGURATION
                }
            } else {
                AiGenerationFailureReason.PROVIDER_FAILURE
            },
            attempts = if (failure == null) 0 else 1,
            providerFailure = failure,
        )
    }
}

private class FakeProfiles : ProfileRepository {
    /** Null models someone who has not finished onboarding. */
    var profile: UserProfile? = DEFAULT_PROFILE

    override fun observeProfile(): Flow<UserProfile?> = MutableStateFlow(profile)

    override suspend fun getProfile(): UserProfile? = profile

    override suspend fun save(profile: UserProfile) = Unit

    override suspend fun deleteAll() = Unit

    private companion object {
        val DEFAULT_PROFILE = UserProfile(
            id = "p",
            goal = com.repforth.core.model.TrainingGoal.STRENGTH,
            experience = com.repforth.core.model.ExperienceLevel.INTERMEDIATE,
            trainingDaysPerWeek = 3,
            sessionLengthMs = FAKE_CEILING_MINUTES * 60_000L,
            availableEquipment = setOf(Equipment.BODY_WEIGHT),
            preferredMuscles = emptySet(),
            exclusions = emptySet(),
        )
    }
}

/** What the screen resolves from `coach_day_default_title` and hands in. */
private val DAY_TITLES = (1..7).map { "Day $it" }

