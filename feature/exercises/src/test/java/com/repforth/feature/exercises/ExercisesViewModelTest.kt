package com.repforth.feature.exercises

import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.InstructionText
import com.repforth.core.model.Language
import com.repforth.core.model.LocalizedInstructions
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle
import com.repforth.core.testing.FakePreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.MovementExclusion
import com.repforth.core.testing.FakeProfiles
import com.repforth.core.testing.sampleProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var preferences: UserPreferencesDataSource
    private lateinit var repository: FakeExerciseRepository
    private lateinit var profiles: FakeProfiles
    private lateinit var viewModel: ExercisesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = UserPreferencesDataSource(FakePreferencesStore())
        repository = FakeExerciseRepository()
        profiles = FakeProfiles(sampleProfile(availableEquipment = setOf(Equipment.BARBELL)))
        viewModel = ExercisesViewModel(repository, preferences, profiles)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selecting an exercise loads full exercise detail and dismissing clears it`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        val summary = repository.testSummary
        viewModel.onSelectExercise(summary)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.selectedExercise)
        assertEquals(summary.id, state.selectedExercise?.id)
        assertEquals("Barbell Bench Press", state.selectedExercise?.name)

        viewModel.onDismissDetail()
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedExercise)
    }

    @Test
    fun `query and filter updates flow to ui state`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        viewModel.onQueryChange("bench")
        viewModel.onEquipmentSelected(Equipment.BARBELL)
        viewModel.onBodyPartSelected(BodyPart.CHEST)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("bench", state.filter.query)
        assertEquals(Equipment.BARBELL, state.filter.equipment)
        assertEquals(BodyPart.CHEST, state.filter.bodyPart)
    }

    // ---- Excluding from the page where the user is looking at it ----

    /**
     * §8 has enforced [ExclusionKind.EXERCISE] since the beginning, and until
     * now nothing in the app could write one — the constraint was real and
     * unreachable, the same shape as the haptics switch that controlled nothing.
     */
    @Test
    fun `excluding an exercise records it and toggling again removes it`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        viewModel.onToggleExcluded(ExerciseId("ex-1"))
        testScheduler.advanceUntilIdle()

        assertEquals(
            setOf("ex-1"),
            profiles.getProfile()!!.exclusions
                .filter { it.kind == ExclusionKind.EXERCISE }.map { it.value }.toSet(),
        )
        assertEquals("The state follows the write", setOf("ex-1"), viewModel.uiState.value.excludedIds)

        viewModel.onToggleExcluded(ExerciseId("ex-1"))
        testScheduler.advanceUntilIdle()

        assertEquals(emptySet<String>(), viewModel.uiState.value.excludedIds)
    }

    /** The kinds share one field, so this must not take the others with it. */
    @Test
    fun `excluding an exercise leaves muscle and movement exclusions alone`() = runTest(dispatcher) {
        profiles.save(
            profiles.getProfile()!!.copy(
                exclusions = setOf(
                    MovementExclusion(ExclusionKind.MUSCLE, Muscle.PECTORALS.slug),
                    MovementExclusion(ExclusionKind.MOVEMENT, "overhead pressing"),
                ),
            ),
        )

        viewModel.onToggleExcluded(ExerciseId("ex-1"))
        testScheduler.advanceUntilIdle()

        val stored = profiles.getProfile()!!.exclusions
        assertEquals(3, stored.size)
        assertEquals(1, stored.count { it.kind == ExclusionKind.MUSCLE })
        assertEquals(1, stored.count { it.kind == ExclusionKind.MOVEMENT })
    }

    /**
     * Excluding does not hide it: the owner's decision is that an exclusion
     * says what the app may programme *for* you, and choosing it by hand is you
     * overriding yourself.
     */
    @Test
    fun `an excluded exercise is still listed`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()
        val before = viewModel.uiState.value.results.size

        viewModel.onToggleExcluded(ExerciseId(viewModel.uiState.value.results.first().id.value))
        testScheduler.advanceUntilIdle()

        assertEquals("The catalog is not filtered by exclusions", before, viewModel.uiState.value.results.size)
    }

}

private class FakeExerciseRepository : ExerciseRepository {
    val testSummary = ExerciseSummary(
        id = ExerciseId("0001"),
        name = "Barbell Bench Press",
        bodyPart = BodyPart.CHEST,
        target = Muscle.PECTORALS,
        equipment = Equipment.BARBELL,
        thumbnail = MediaRef("https://example.com/0001.jpg", "sha", 100L),
    )

    private val testExercise = Exercise(
        id = ExerciseId("0001"),
        name = "Barbell Bench Press",
        bodyPart = BodyPart.CHEST,
        target = Muscle.PECTORALS,
        muscleGroup = Muscle.CHEST,
        secondaryMuscles = setOf(Muscle.DELTOIDS, Muscle.TRICEPS),
        equipment = Equipment.BARBELL,
        instructions = LocalizedInstructions(
            mapOf(
                Language.ENGLISH to InstructionText(listOf("Lie flat on the bench.", "Press the bar upward.")),
                Language.TURKISH to InstructionText(listOf("Sehpaya düz yatın.", "Barı yukarı doğru itin.")),
            ),
        ),
        thumbnail = MediaRef("https://example.com/0001.jpg", "sha", 100L),
        animation = MediaRef("https://example.com/0001.gif", "sha", 500L),
    )

    override suspend fun count(): Int = 1

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> =
        flowOf(listOf(testSummary))

    override suspend fun find(id: ExerciseId): Exercise? =
        if (id == testSummary.id) testExercise else null

    override suspend fun candidates(): List<ExerciseCandidate> = emptyList()

    override suspend fun summaries(ids: Collection<ExerciseId>): Map<ExerciseId, ExerciseSummary> =
        if (testSummary.id in ids) mapOf(testSummary.id to testSummary) else emptyMap()
}

