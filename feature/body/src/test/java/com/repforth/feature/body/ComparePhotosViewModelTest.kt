package com.repforth.feature.body

import com.repforth.core.model.CheckIn
import com.repforth.core.model.Pose
import com.repforth.core.testing.FakeBodyRepository
import com.repforth.core.testing.FakePhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComparePhotosViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val body = FakeBodyRepository(today = BodyFixtures.TODAY)
    private val today = BodyFixtures.TODAY

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ComparePhotosViewModel(body, FakePhotoStore())

    private suspend fun ComparePhotosViewModel.state(where: (ComparePhotosUiState) -> Boolean = { true }) =
        uiState.filterNotNull().first(where)

    /** Front on three days, side on two of them, back on one. */
    private suspend fun seed() {
        body.save(
            CheckIn("a", today.minusDays(60), mapOf("weight" to 86.0), mapOf(Pose.FRONT to "a-f", Pose.SIDE to "a-s")),
        )
        body.save(CheckIn("b", today.minusDays(30), emptyMap(), mapOf(Pose.FRONT to "b-f", Pose.BACK to "b-b")))
        body.save(CheckIn("c", today, mapOf("weight" to 82.0), mapOf(Pose.FRONT to "c-f", Pose.SIDE to "c-s")))
    }

    @Test
    fun `defaults to the earliest and latest with that pose`() = runTest(dispatcher) {
        seed()

        val state = viewModel().state()

        assertEquals(Pose.FRONT, state.pose)
        assertEquals(listOf(today.minusDays(60), today.minusDays(30), today), state.dates)
        assertEquals("a-f", state.left?.fileName)
        assertEquals("c-f", state.right?.fileName)
    }

    @Test
    fun `a chosen date replaces one side`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModel()
        viewModel.state()

        viewModel.onLeftDate(today.minusDays(30))

        val state = viewModel.state { it.left?.date == today.minusDays(30) }
        assertEquals("b-f", state.left?.fileName)
        assertEquals("c-f", state.right?.fileName)
    }

    @Test
    fun `switching pose re-picks both sides`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onLeftDate(today.minusDays(30))
        viewModel.state { it.left?.date == today.minusDays(30) }

        viewModel.onPose(Pose.SIDE)

        val state = viewModel.state { it.pose == Pose.SIDE }
        assertEquals(listOf(today.minusDays(60), today), state.dates)
        assertEquals("a-s", state.left?.fileName)
        assertEquals("c-s", state.right?.fileName)
    }

    @Test
    fun `one photo of a pose cannot be compared`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModel()
        viewModel.state()

        viewModel.onPose(Pose.BACK)

        val state = viewModel.state { it.pose == Pose.BACK }
        assertEquals(1, state.dates.size)
        assertNull(state.left)
        assertNull(state.right)
    }

    @Test
    fun `the caption carries that day's weight`() = runTest(dispatcher) {
        seed()
        val viewModel = viewModel()
        viewModel.state()
        viewModel.onRightDate(today.minusDays(30))

        val state = viewModel.state { it.right?.date == today.minusDays(30) }

        assertEquals(86.0, state.left?.weightKg)
        assertNull("No weight was recorded that day", state.right?.weightKg)
    }

    /** Someone who has only ever taken side photos should land on something they can compare. */
    @Test
    fun `opens on the first pose that can be compared`() = runTest(dispatcher) {
        body.save(CheckIn("a", today.minusDays(10), emptyMap(), mapOf(Pose.SIDE to "a-s")))
        body.save(CheckIn("b", today, emptyMap(), mapOf(Pose.SIDE to "b-s", Pose.FRONT to "b-f")))

        val state = viewModel().state()

        assertEquals(Pose.SIDE, state.pose)
        assertEquals("a-s", state.left?.fileName)
    }
}
