package com.repforth.feature.builder

import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.ai.AI_WORKOUT_SCHEMA_VERSION
import com.repforth.core.ai.AiGenerationFailureReason
import com.repforth.core.ai.AiPlannedDay
import com.repforth.core.ai.AiPlannedExercise
import com.repforth.core.ai.AiWorkoutGenerationOutcome
import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutResponse
import com.repforth.core.ai.ProviderAvailability
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.allMuscles
import com.repforth.core.model.synonyms
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.InstructionText
import com.repforth.core.model.Language
import com.repforth.core.model.LocalizedInstructions
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WeekDay
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
        viewModel = BuilderViewModel(
            templates,
            catalog,
            profiles,
            generator,
            AlwaysConfigured,
            weeks,
            UserPreferencesDataSource(FakePreferencesStore()),
        )
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

    // ---- "Discard workout?" means changed, not merely populated ----

    /**
     * Opening a saved week to look at it and pressing back asked whether to
     * discard the workout.
     *
     * The check was `exercises.isNotEmpty() || weekDays.isNotEmpty() ||
     * name.isNotBlank()`, which is "has content" — true of everything the
     * builder ever shows, including a plan loaded a moment ago and untouched.
     * Reported from a device, on both the week and the day paths.
     */
    @Test
    fun `a week just opened is not dirty`() = runTest(dispatcher) {
        weeks.save(savedWeek())

        viewModel.loadWeek("w1")
        advanceUntilIdle()

        assertFalse("Nothing has been changed", state.isDirty)
    }

    @Test
    fun `a plan just opened is not dirty`() = runTest(dispatcher) {
        templates.save(savedTemplate("t1", "Push", "a"))

        viewModel.load("t1")
        advanceUntilIdle()

        assertFalse(state.isDirty)
    }

    /** Looking is not editing: a day accordion opens and closes freely. */
    @Test
    fun `expanding a day is not a change`() = runTest(dispatcher) {
        weeks.save(savedWeek())
        viewModel.loadWeek("w1")
        advanceUntilIdle()

        viewModel.onToggleDayExpanded(1)
        viewModel.onToggleDayExpanded(0)

        assertFalse("Collapsing a day is not editing it", state.isDirty)
    }

    @Test
    fun `editing a loaded week is dirty`() = runTest(dispatcher) {
        weeks.save(savedWeek())
        viewModel.loadWeek("w1")
        advanceUntilIdle()

        viewModel.onDayTitleChange(0, "Chest day")

        assertTrue(state.isDirty)
    }

    @Test
    fun `changing a number on a loaded plan is dirty`() = runTest(dispatcher) {
        templates.save(savedTemplate("t1", "Push", "a"))
        viewModel.load("t1")
        advanceUntilIdle()

        viewModel.onSetsChange(0, 5)

        assertTrue(state.isDirty)
    }

    /** A generated week has never been saved, so leaving it does lose something. */
    @Test
    fun `a freshly generated week is dirty`() = runTest(dispatcher) {
        catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))
        generator.response = AiWorkoutResponse(
            days = listOf(day("Push", "a"), day("Pull", "a")),
            rationale = "Split",
        )

        viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        assertTrue("Nothing has written this down yet", state.isDirty)
    }

    /** And saving it settles the question. */
    @Test
    fun `saving makes it clean again`() = runTest(dispatcher) {
        templates.save(savedTemplate("t1", "Push", "a"))
        viewModel.load("t1")
        advanceUntilIdle()
        viewModel.onSetsChange(0, 5)
        assertTrue(state.isDirty)

        viewModel.onSave()
        advanceUntilIdle()

        assertFalse(state.isDirty)
    }

    private fun savedWeek() = TrainingWeek(
        id = "w1",
        name = "PPL Week",
        source = PlanSource.AI,
        active = false,
        days = listOf(
            WeekDay(0, "Push", workout = savedTemplate("d0", "Push", "a")),
            WeekDay(1, "Pull", workout = savedTemplate("d1", "Pull", "b")),
        ),
    )

    // ---- Reopening a saved week ----

    /**
     * A week could be generated, saved, and then never edited again.
     *
     * `load` handled templates only, so the builder had no way back into a week
     * — and once Plans made a week's *day* rows tappable, the app answered half
     * the question: day three could be edited while the week it belonged to
     * could not be renamed, reordered or given another day.
     */
    @Test
    fun `a saved week reopens as its days`() = runTest(dispatcher) {
        val week = TrainingWeek(
            id = "w1",
            name = "PPL Week",
            source = PlanSource.AI,
            active = false,
            days = listOf(
                WeekDay(0, "Push", workout = savedTemplate("d0", "Push", "a")),
                WeekDay(1, "Pull", workout = savedTemplate("d1", "Pull", "b")),
            ),
        )
        weeks.save(week)

        viewModel.loadWeek("w1")
        advanceUntilIdle()

        assertEquals("w1", state.weekId)
        assertEquals("PPL Week", state.name)
        assertEquals(PlanSource.AI, state.source)
        assertEquals(listOf("Push", "Pull"), state.weekDays.map { it.title })
        assertEquals(listOf(0, 1), state.weekDays.map { it.dayIndex })
        assertEquals("a", state.weekDays[0].exercises.single().exerciseId.value)
        assertTrue("A reopened week is the week being edited", state.isWeeklyPlan)
    }

    /**
     * The day keeps the template id it was saved under.
     *
     * A fresh id per load would detach every day from the workout history
     * recorded against it, which is how Today knows what has been done — and
     * saving afterwards would leave the old rows orphaned.
     */
    @Test
    fun `reopening a week keeps each day's template id`() = runTest(dispatcher) {
        weeks.save(
            TrainingWeek(
                id = "w1",
                name = "Week",
                source = PlanSource.AI,
                active = false,
                days = listOf(WeekDay(0, "Push", workout = savedTemplate("day-0", "Push", "a"))),
            ),
        )

        viewModel.loadWeek("w1")
        advanceUntilIdle()

        assertEquals("day-0", state.weekDays.single().templateId)
    }

    /** A week id is not a plan id; letting them share made every re-save mint a week. */
    @Test
    fun `reopening a week does not put its id in planId`() = runTest(dispatcher) {
        weeks.save(
            TrainingWeek(
                id = "w1",
                name = "Week",
                source = PlanSource.AI,
                active = false,
                days = listOf(WeekDay(0, "Push", workout = savedTemplate("d0", "Push", "a"))),
            ),
        )

        viewModel.loadWeek("w1")
        advanceUntilIdle()

        assertNull(state.planId)
    }

    private fun savedTemplate(id: String, name: String, exerciseId: String) = WorkoutTemplate(
        id = id,
        name = name,
        source = PlanSource.AI,
        exercises = listOf(
            PlannedExercise(
                id = "$id-0",
                exerciseId = ExerciseId(exerciseId),
                position = 0,
                target = ExerciseTarget.Reps(sets = 3, reps = 10),
                restMs = 60_000L,
            ),
        ),
    )

    // ---- Coach's plan shape: seeded from the profile, overridable for one plan ----

    @Test
    fun `coach is seeded from the profile`() = runTest(dispatcher) {
        advanceUntilIdle()

        assertEquals(TrainingGoal.STRENGTH, state.coachGoal)
        assertEquals(ExperienceLevel.INTERMEDIATE, state.coachExperience)
        assertEquals(FAKE_CEILING_MINUTES.toInt(), state.coachSessionMinutes)
        assertEquals(3, state.coachDays)
        assertFalse("Nothing has been changed yet", state.coachDiffersFromProfile)
    }

    /**
     * Changing Coach must not change the profile, and must change the request.
     *
     * The three fields shape every generated week and none of them were on the
     * screen before; the point of putting them there is that they can differ
     * from the standing profile for one plan.
     */
    @Test
    fun `coach overrides reach the generation request without touching the profile`() =
        runTest(dispatcher) {
            catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))
            advanceUntilIdle()

            viewModel.onCoachGoalChange(TrainingGoal.ENDURANCE)
            viewModel.onCoachExperienceChange(ExperienceLevel.BEGINNER)
            viewModel.onCoachSessionMinutesChange(90)
            viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
            advanceUntilIdle()

            val request = generator.requests.single()
            assertEquals(TrainingGoal.ENDURANCE, request.goal)
            assertEquals(ExperienceLevel.BEGINNER, request.experience)
            assertEquals(90 * 60_000L, request.sessionLengthMs)

            assertTrue("The profile is a standing fact, not this plan", profiles.saved.isEmpty())
            assertEquals(TrainingGoal.STRENGTH, profiles.profile?.goal)
        }

    /** An untouched Coach sends the profile's own values, and no overrides. */
    @Test
    fun `coach left alone asks for exactly what the profile says`() = runTest(dispatcher) {
        catalog.catalog = listOf(candidate("a", Muscle.PECTORALS))
        advanceUntilIdle()

        viewModel.onGenerate("Coach plan", DAY_TITLES, Language.ENGLISH)
        advanceUntilIdle()

        val request = generator.requests.single()
        assertNull(request.goalOverride)
        assertNull(request.experienceOverride)
        assertNull(request.sessionLengthMsOverride)
        assertEquals(TrainingGoal.STRENGTH, request.goal)
    }

    @Test
    fun `save as default writes every part of the shape at once`() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.onCoachGoalChange(TrainingGoal.ENDURANCE)
        viewModel.onCoachExperienceChange(ExperienceLevel.ADVANCED)
        viewModel.onCoachSessionMinutesChange(75)
        viewModel.onCoachDaysChange(5)

        viewModel.onSaveCoachDefaults()
        advanceUntilIdle()

        val saved = profiles.saved.single()
        assertEquals(TrainingGoal.ENDURANCE, saved.goal)
        assertEquals(ExperienceLevel.ADVANCED, saved.experience)
        assertEquals(5, saved.trainingDaysPerWeek)
        assertEquals(75 * 60_000L, saved.sessionLengthMs)
        assertFalse("Coach now agrees with the profile", state.coachDiffersFromProfile)
    }

    /** A button that writes what is already stored looks broken. */
    @Test
    fun `save as default does nothing when nothing differs`() = runTest(dispatcher) {
        advanceUntilIdle()

        viewModel.onSaveCoachDefaults()
        advanceUntilIdle()

        assertTrue(profiles.saved.isEmpty())
    }

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
                days = listOf(
                    AiPlannedDay(
                        title = "Push",
                        exercises = listOf(
                            AiPlannedExercise(
                                exerciseId = "a",
                                sets = 4,
                                repetitions = 12,
                                restSeconds = 75,
                            ),
                            AiPlannedExercise(
                                exerciseId = "b",
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
            days = listOf(
                AiPlannedDay(
                    title = "",
                    exercises = listOf(
                        AiPlannedExercise(
                            exerciseId = "a",
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
            days = listOf(
                AiPlannedDay(
                    title = "",
                    exercises = listOf(
                        AiPlannedExercise(
                            exerciseId = "a",
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
            title = "Upper Push",
            exercises = listOf(
                AiPlannedExercise("a", sets = 3, repetitions = 10, restSeconds = 60),
            ),
        )
        val day1 = AiPlannedDay(
            title = "Lower & Core",
            exercises = listOf(
                AiPlannedExercise("b", sets = 3, repetitions = 12, restSeconds = 60),
            ),
        )
        generator.response = AiWorkoutResponse(
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
            days = listOf(day("Push", "a"), day("Pull", "b")),
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
            days = listOf(day("Day 1", "a")),
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
            days = listOf(day("Push", "a"), day("Pull", "b")),
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
            days = listOf(day("Push", "a"), day("Pull", "b")),
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
            title = "Day 1",
            exercises = listOf(
                AiPlannedExercise("a", sets = 3, repetitions = 10, restSeconds = 60),
                AiPlannedExercise("b", sets = 3, repetitions = 10, restSeconds = 60),
            ),
        )
        val day1 = AiPlannedDay(
            title = "Day 2",
            exercises = listOf(
                AiPlannedExercise("c", sets = 3, repetitions = 10, restSeconds = 60),
            ),
        )
        generator.response = AiWorkoutResponse(
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

private fun day(title: String, vararg exerciseIds: String) = AiPlannedDay(
    title = title,
    exercises = exerciseIds.map { AiPlannedExercise(it, sets = 3, repetitions = 10, restSeconds = 60) },
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

class BuilderExerciseDetailTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var catalog: FakeExercises
    private lateinit var viewModel: BuilderViewModel

    private val bench = Exercise(
        id = ExerciseId("ex-1"),
        name = "barbell bench press",
        bodyPart = BodyPart.CHEST,
        target = Muscle.PECTORALS,
        muscleGroup = Muscle.PECTORALS,
        secondaryMuscles = setOf(Muscle.TRICEPS),
        equipment = Equipment.BARBELL,
        instructions = LocalizedInstructions(
            mapOf(
                Language.ENGLISH to InstructionText(listOf("Press the bar up.")),
                Language.TURKISH to InstructionText(listOf("Barı yukarı it.")),
            ),
        ),
        thumbnail = MediaRef.Unavailable,
        animation = MediaRef.Unavailable,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        catalog = FakeExercises()
        catalog.details = mapOf(bench.id to bench)
        viewModel = BuilderViewModel(
            RecordingTemplateRepository(),
            catalog,
            FakeProfiles(),
            FakeWorkoutGenerator(),
            AlwaysConfigured,
            RecordingWeekRepository(),
            UserPreferencesDataSource(FakePreferencesStore()),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The builder could show what an exercise was nowhere at all: the detail
     * sheet was reachable from the catalog tab and the picker, so once a row was
     * in a plan there was no way to see how to perform it. Noticed on a
     * generated week, where nobody chose the exercises by hand.
     */
    @Test
    fun `a row in the plan can open its catalog entry`() = runTest(dispatcher) {
        viewModel.onShowExerciseDetail(bench.id)
        advanceUntilIdle()

        assertEquals(bench, viewModel.uiState.value.detailExercise)
    }

    @Test
    fun `dismissing closes the sheet`() = runTest(dispatcher) {
        viewModel.onShowExerciseDetail(bench.id)
        advanceUntilIdle()
        viewModel.onDismissExerciseDetail()

        assertNull(viewModel.uiState.value.detailExercise)
    }

    /**
     * A plan can outlive an exercise the catalog no longer ships. The row keeps
     * its id as a name in that case; tapping it must open nothing rather than
     * take the screen down.
     */
    @Test
    fun `a row whose exercise has left the catalog opens nothing`() = runTest(dispatcher) {
        viewModel.onShowExerciseDetail(ExerciseId("vanished"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.detailExercise)
    }
}

private class FakeExercises : ExerciseRepository {
    var catalog: List<ExerciseCandidate> = emptyList()

    /** Full records, for the detail sheet. Empty unless a test needs one. */
    var details: Map<ExerciseId, Exercise> = emptyMap()

    override suspend fun candidates(): List<ExerciseCandidate> = catalog

    override suspend fun count(): Int = 0

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> = emptyFlow()

    override suspend fun find(id: ExerciseId): Exercise? = details[id]

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
    val requests = mutableListOf<GenerationRequest>()

    override suspend fun generate(
        request: GenerationRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiWorkoutGenerationOutcome {
        locales += locale
        requests += request
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

    val saved = mutableListOf<UserProfile>()

    override suspend fun save(profile: UserProfile) {
        saved += profile
        this.profile = profile
    }

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


/**
 * A provider is set up, which is the case every test here is about.
 *
 * The gate is covered by `CoachComposeTest` instead. Answering "configured" is
 * what the whole `ProviderAvailability` interface exists for — a view model
 * test should not have to build a keystore to say it.
 */
private object AlwaysConfigured : ProviderAvailability {
    override val configured = kotlinx.coroutines.flow.flowOf(true)
}
