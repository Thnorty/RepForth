package com.repforth.feature.body

import com.repforth.core.model.CheckIn
import com.repforth.core.testing.FakeBodyRepository
import com.repforth.core.testing.FakePhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val body = FakeBodyRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening the tab sweeps photos once`() = runTest(dispatcher) {
        BodyViewModel(body, FakePhotoStore())
        advanceUntilIdle()

        assertEquals(1, body.sweeps)
    }

    @Test
    fun `a saved check-in reaches the state`() = runTest(dispatcher) {
        val viewModel = BodyViewModel(body, FakePhotoStore())
        body.save(CheckIn("a", BodyFixtures.TODAY, mapOf("waist" to 86.0), emptyMap()))

        val state = viewModel.uiState.first { !it.loading }

        assertEquals(86.0, state.rows.single { it.type.id == "waist" }.latest, 0.0)
    }
}
