package com.repforth.feature.body

import androidx.lifecycle.SavedStateHandle
import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.CheckIn
import com.repforth.core.testing.FakeBodyRepository
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val body = FakeBodyRepository(today = BodyFixtures.TODAY)
    private val today = BodyFixtures.TODAY

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MeasurementDetailViewModel(
        SavedStateHandle(mapOf("typeId" to "waist")),
        body,
        FakeTimeSource(wallClock = 1_791_892_800_000L),
        ZoneOffset.UTC,
    )

    private suspend fun seed() {
        body.save(CheckIn("a", today.minusMonths(1), mapOf("waist" to 88.0), emptyMap()))
        body.save(CheckIn("b", today.minusMonths(4), mapOf("waist" to 90.0), emptyMap()))
        body.save(CheckIn("c", today.minusMonths(13), mapOf("waist" to 93.0), emptyMap()))
        body.save(CheckIn("d", today.minusMonths(2), mapOf("weight" to 80.0), emptyMap()))
    }

    private suspend fun MeasurementDetailViewModel.loaded() = uiState.first { it.type != null }

    @Test
    fun `the range limits the points`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModel()

        assertEquals(1, viewModel.loaded().points.size)
        viewModel.onRange(TrendRange.SIX)
        assertEquals(2, viewModel.uiState.first { it.range == TrendRange.SIX }.points.size)
        viewModel.onRange(TrendRange.YEAR)
        assertEquals(2, viewModel.uiState.first { it.range == TrendRange.YEAR }.points.size)
        viewModel.onRange(TrendRange.ALL)
        assertEquals(3, viewModel.uiState.first { it.range == TrendRange.ALL }.points.size)
    }

    @Test
    fun `entries are every entry of that type, newest first`() = runTest(dispatcher) {
        seed()

        val entries = viewModel().loaded().entries

        assertEquals(listOf("a", "b", "c"), entries.map { it.checkInId })
    }

    @Test
    fun `a type with no entries has no points`() = runTest(dispatcher) {
        val state = viewModel().loaded()

        assertTrue(state.points.isEmpty())
        assertTrue(state.entries.isEmpty())
    }
}
