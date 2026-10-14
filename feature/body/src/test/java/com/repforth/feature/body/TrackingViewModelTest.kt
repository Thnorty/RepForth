package com.repforth.feature.body

import com.repforth.core.model.CheckIn
import com.repforth.core.model.MeasurementKind
import com.repforth.core.testing.FakeBodyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val body = FakeBodyRepository(today = BodyFixtures.TODAY)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrackingViewModel(body)

    private fun custom(name: String) = body.types.value.single { it.customName == name }

    @Test
    fun `toggling a type persists it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onToggle("neck", true)
        advanceUntilIdle()

        assertTrue(body.types.value.single { it.id == "neck" }.tracked)
    }

    @Test
    fun `a custom type is added after the built-ins`() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertNull(viewModel.onAdd("Skinfold", MeasurementKind.PLAIN, "mm"))
        advanceUntilIdle()

        val added = custom("Skinfold")
        assertEquals("mm", added.unitLabel)
        assertTrue(body.types.value.filter { it.builtIn != null }.all { it.sortOrder < added.sortOrder })
    }

    @Test
    fun `the name is trimmed`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onAdd("  Grip  ", MeasurementKind.MASS, null)
        advanceUntilIdle()

        assertEquals(MeasurementKind.MASS, custom("Grip").kind)
    }

    @Test
    fun `a blank name is refused`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val before = body.types.value.size

        assertEquals(CustomProblem.NAME_BLANK, viewModel.onAdd("   ", MeasurementKind.LENGTH, null))
        advanceUntilIdle()

        assertEquals(before, body.types.value.size)
    }

    @Test
    fun `a name over forty characters is refused`() = runTest(dispatcher) {
        assertEquals(CustomProblem.NAME_TOO_LONG, viewModel().onAdd("x".repeat(41), MeasurementKind.LENGTH, null))
    }

    @Test
    fun `a number kind needs a unit label`() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertEquals(CustomProblem.UNIT_MISSING, viewModel.onAdd("Grip", MeasurementKind.PLAIN, " "))
        assertEquals(CustomProblem.UNIT_TOO_LONG, viewModel.onAdd("Grip", MeasurementKind.PLAIN, "x".repeat(11)))
    }

    @Test
    fun `renaming refuses a blank name and keeps the old one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAdd("Grip", MeasurementKind.MASS, null)
        advanceUntilIdle()
        val id = custom("Grip").id

        assertEquals(CustomProblem.NAME_BLANK, viewModel.onRename(id, " "))
        assertNull(viewModel.onRename(id, " Grip strength "))
        advanceUntilIdle()

        assertEquals("Grip strength", body.types.value.single { it.id == id }.customName)
    }

    @Test
    fun `delete asks with the entry count, then removes it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAdd("Grip", MeasurementKind.MASS, null)
        advanceUntilIdle()
        val id = custom("Grip").id
        body.save(CheckIn("a", BodyFixtures.TODAY.minusDays(1), mapOf(id to 50.0), emptyMap()))
        body.save(CheckIn("b", BodyFixtures.TODAY, mapOf(id to 52.0), emptyMap()))

        viewModel.onAskDelete(id)
        val asking = viewModel.uiState.filterNotNull().first { it.pendingDelete != null }

        assertEquals(2, asking.pendingDelete?.entries)
        assertEquals("Grip", asking.pendingDelete?.type?.customName)

        viewModel.onConfirmDelete()
        val done = viewModel.uiState.filterNotNull().first { it.pendingDelete == null && it.types.none { t -> t.id == id } }

        assertTrue(done.types.none { it.id == id })
    }

    @Test
    fun `cancelling a delete keeps the type`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onAdd("Grip", MeasurementKind.MASS, null)
        advanceUntilIdle()
        val id = custom("Grip").id

        viewModel.onAskDelete(id)
        viewModel.uiState.filterNotNull().first { it.pendingDelete != null }
        viewModel.onCancelDelete()
        advanceUntilIdle()

        assertTrue(body.types.value.any { it.id == id })
        assertNull(viewModel.uiState.filterNotNull().first().pendingDelete)
    }
}
