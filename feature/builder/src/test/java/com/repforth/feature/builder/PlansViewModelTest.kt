package com.repforth.feature.builder

import com.repforth.core.model.PlanSource
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.WeekRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var templates: FakeTemplateRepository
    private lateinit var weeks: FakeWeekRepository
    private lateinit var viewModel: PlansViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        templates = FakeTemplateRepository()
        weeks = FakeWeekRepository()
        viewModel = PlansViewModel(templates, weeks)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observes templates and weekly programs`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.plans.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.weeklyPlans.collect {} }

        val plan = WorkoutTemplate(id = "p1", name = "Push", source = PlanSource.MANUAL, exercises = emptyList())
        val week = TrainingWeek(id = "w1", name = "PPL", source = PlanSource.AI, active = true, days = emptyList())

        templates.emit(listOf(plan))
        weeks.emit(listOf(week))
        advanceUntilIdle()

        assertEquals(listOf(plan), viewModel.plans.value)
        assertEquals(listOf(week), viewModel.weeklyPlans.value)
    }

    @Test
    fun `deleting a plan removes it from repository`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.plans.collect {} }

        val plan = WorkoutTemplate(id = "p1", name = "Push", source = PlanSource.MANUAL, exercises = emptyList())
        templates.emit(listOf(plan))
        advanceUntilIdle()

        viewModel.onDelete("p1")
        advanceUntilIdle()

        assertTrue(templates.deleted.contains("p1"))
    }

    @Test
    fun `deleting a week removes it from repository`() = runTest(dispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.weeklyPlans.collect {} }

        val week = TrainingWeek(id = "w1", name = "PPL", source = PlanSource.AI, active = true, days = emptyList())
        weeks.emit(listOf(week))
        advanceUntilIdle()

        viewModel.onDeleteWeek("w1")
        advanceUntilIdle()

        assertTrue(weeks.deleted.contains("w1"))
    }

    @Test
    fun `setting active week marks it active in repository`() = runTest(dispatcher) {
        viewModel.onSetActiveWeek("w1")
        advanceUntilIdle()

        assertEquals("w1", weeks.activeId)
    }
}

private class FakeTemplateRepository : TemplateRepository {
    val deleted = mutableListOf<String>()
    private val all = MutableStateFlow<List<WorkoutTemplate>>(emptyList())

    fun emit(list: List<WorkoutTemplate>) {
        all.value = list
    }

    override fun observeAll(): Flow<List<WorkoutTemplate>> = all
    override suspend fun find(id: String): WorkoutTemplate? = all.value.firstOrNull { it.id == id }
    override suspend fun save(template: WorkoutTemplate) = Unit
    override suspend fun delete(id: String) {
        deleted += id
        all.value = all.value.filter { it.id != id }
    }
    override suspend fun deleteAll() = Unit
}

private class FakeWeekRepository : WeekRepository {
    val deleted = mutableListOf<String>()
    var activeId: String? = null
    private val all = MutableStateFlow<List<TrainingWeek>>(emptyList())
    private val active = MutableStateFlow<TrainingWeek?>(null)

    fun emit(list: List<TrainingWeek>) {
        all.value = list
    }

    override fun observeAll(): Flow<List<TrainingWeek>> = all
    override fun observeActive(): Flow<TrainingWeek?> = active
    override suspend fun find(id: String): TrainingWeek? = all.value.firstOrNull { it.id == id }
    override suspend fun save(week: TrainingWeek) = Unit
    override suspend fun setActive(id: String) {
        activeId = id
    }
    override suspend fun delete(id: String) {
        deleted += id
        all.value = all.value.filter { it.id != id }
    }
    override suspend fun deleteAll() = Unit
}
