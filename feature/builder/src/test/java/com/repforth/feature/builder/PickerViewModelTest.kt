package com.repforth.feature.builder

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var preferences: UserPreferencesDataSource
    private lateinit var repository: FakePickerRepository
    private lateinit var viewModel: PickerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = UserPreferencesDataSource(FakePreferencesStore())
        repository = FakePickerRepository()
        viewModel = PickerViewModel(repository, preferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selecting an exercise loads full detail and dismissing clears it`() = runTest(dispatcher) {
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
    fun `query update flows to ui state`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        viewModel.onQueryChange("press")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("press", state.query)
    }
}

private class FakePickerRepository : ExerciseRepository {
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
