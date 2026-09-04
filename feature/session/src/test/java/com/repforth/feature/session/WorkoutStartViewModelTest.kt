package com.repforth.feature.session

import com.repforth.core.common.time.FakeTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The question asked before leaving the plan list.
 *
 * The conflict used to be raised inside the workout screen, so the user was
 * moved into a workout they had not chosen and asked about it afterwards. This
 * covers the version that asks first — and, more importantly, that asking does
 * not itself start anything: the whole point is that the running workout is
 * still untouched while the question is on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutStartViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sessions: StartFakeSessions
    private lateinit var controller: SessionController

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessions = StartFakeSessions()
        controller = SessionController(sessions, StartFakeTemplates(), FakeTimeSource())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with nothing running, a tapped plan opens straight away`() = runTest(dispatcher) {
        val starter = starter()

        starter.request(PUSH)
        testScheduler.advanceUntilIdle()

        assertEquals(StartIntent.Open(PUSH), starter.intent.value)
    }

    /** Tapping the workout that is already going means "take me back to it". */
    @Test
    fun `tapping the plan that is already running opens it without asking`() = runTest(dispatcher) {
        controller.start(PUSH)
        val starter = starter()

        starter.request(PUSH)
        testScheduler.advanceUntilIdle()

        assertEquals(StartIntent.Open(PUSH), starter.intent.value)
    }

    /**
     * The reason this class exists.
     *
     * A different workout is running, so the answer is a question — and no
     * navigation, which is what "before going inside the workout" means.
     */
    @Test
    fun `tapping a different plan asks first and names the workout in the way`() = runTest(dispatcher) {
        controller.start(PUSH)
        val starter = starter()

        starter.request(PULL)
        testScheduler.advanceUntilIdle()

        val intent = starter.intent.value
        assertTrue("Expected Conflict, got $intent", intent is StartIntent.Conflict)
        assertEquals(PUSH, (intent as StartIntent.Conflict).runningName)
        assertEquals(PULL, intent.requestedTemplateId)
    }

    /**
     * Asking must not change anything.
     *
     * If merely raising the question started, ended or swapped a workout, the
     * "go back to it" answer would be a lie by the time it was tapped.
     */
    @Test
    fun `asking leaves the running workout exactly as it was`() = runTest(dispatcher) {
        controller.start(PUSH)
        val before = controller.state.value
        val starter = starter()

        starter.request(PULL)
        testScheduler.advanceUntilIdle()

        assertEquals(before, controller.state.value)
    }

    @Test
    fun `keeping the running workout opens it rather than the plan that was tapped`() = runTest(dispatcher) {
        controller.start(PUSH)
        val starter = starter()
        starter.request(PULL)
        testScheduler.advanceUntilIdle()

        starter.keepRunning()

        // A null template is "whatever is running", not "nothing" -- the screen
        // resumes instead of starting.
        assertEquals(StartIntent.Open(null), starter.intent.value)
        assertEquals(PUSH, controller.state.value?.templateId)
    }

    @Test
    fun `discarding ends the running workout and opens the tapped one`() = runTest(dispatcher) {
        controller.start(PUSH)
        val starter = starter()
        starter.request(PULL)
        testScheduler.advanceUntilIdle()

        starter.discardAndStart()
        testScheduler.advanceUntilIdle()

        assertEquals(StartIntent.Open(PULL), starter.intent.value)
        assertEquals(PULL, controller.state.value?.templateId)
    }

    /** Both answers lead into a workout; neither leaves the user where they were. */
    @Test
    fun `both answers open a workout`() = runTest(dispatcher) {
        controller.start(PUSH)

        val kept = starter().also {
            it.request(PULL); testScheduler.advanceUntilIdle(); it.keepRunning()
        }
        assertTrue(kept.intent.value is StartIntent.Open)

        val discarded = starter().also {
            it.request(PULL); testScheduler.advanceUntilIdle()
            it.discardAndStart(); testScheduler.advanceUntilIdle()
        }
        assertTrue(discarded.intent.value is StartIntent.Open)
    }

    /**
     * The third answer: never mind.
     *
     * Back and a tap outside both land here. Unlike the two buttons it goes
     * nowhere — which is only safe because the question is asked before leaving
     * the list, so "nowhere" is somewhere the user already was.
     */
    @Test
    fun `dismissing the question goes nowhere and changes nothing`() = runTest(dispatcher) {
        controller.start(PUSH)
        val before = controller.state.value
        val starter = starter()
        starter.request(PULL)
        testScheduler.advanceUntilIdle()

        starter.cancel()
        testScheduler.advanceUntilIdle()

        // No Open intent, so the shell does not navigate.
        assertNull(starter.intent.value)
        // And the workout that was running is untouched -- not resumed, not
        // abandoned, not swapped.
        assertEquals(before, controller.state.value)
        assertEquals(PUSH, controller.state.value?.templateId)
    }

    @Test
    fun `the shell clearing the intent stops it firing twice`() = runTest(dispatcher) {
        val starter = starter()
        starter.request(PUSH)
        testScheduler.advanceUntilIdle()

        starter.consumed()

        assertNull(starter.intent.value)
    }

    private fun starter() = WorkoutStartViewModel(controller, StartFakeTemplates())

    private companion object {
        const val PUSH = "push-day"
        const val PULL = "pull-day"
    }
}
