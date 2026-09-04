package com.repforth.feature.session

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The running workout, without a device.
 *
 * The engine is already tested exhaustively; what is untested until here is the
 * wiring around it — that every applied transition is written down before
 * anything acts on it (§10), that a rejected command writes nothing, and that
 * the rest countdown is recomputed from a deadline rather than decremented.
 */
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.media.download.MediaDownloader
import com.repforth.core.media.download.MediaPrefetchRequest
import com.repforth.core.model.MediaRef
import com.repforth.core.testing.FakePreferencesStore
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val time = FakeTimeSource()
    private lateinit var sessions: RecordingSessionRepository
    private lateinit var controller: SessionController
    private lateinit var viewModel: SessionViewModel
    private lateinit var preferences: UserPreferencesDataSource
    private lateinit var downloader: FakeMediaDownloader

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessions = RecordingSessionRepository()
        controller = SessionController(sessions, FakeTemplates(), time)
        preferences = UserPreferencesDataSource(FakePreferencesStore())
        downloader = FakeMediaDownloader()
        viewModel = SessionViewModel(controller, FakeExercises(), FakeTemplates(), preferences, downloader)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `starting a plan begins the first exercise and persists it`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.ACTIVE, state.phase)
        assertEquals("Bench press", state.currentName)
        assertEquals(1, state.setNumber)
        assertEquals(3, state.setTotal)
        assertTrue("Every transition must be written down", sessions.persisted.size >= 2)
    }

    /**
     * A workout in progress must survive tapping Start again. The repository
     * enforces one active session too, but a second engine start here would
     * have already replaced the snapshot in memory before the write.
     */
    @Test
    fun `starting again while one is running does nothing`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        val sessionId = state.snapshot?.sessionId
        val writes = sessions.persisted.size

        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()

        assertEquals(sessionId, state.snapshot?.sessionId)
        assertEquals(writes, sessions.persisted.size)
    }

    @Test
    fun `completing a set moves into rest and records what was done`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()

        viewModel.onCompleteSet(reps = 8, weightKg = 60.0, durationMs = null)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.RESTING, state.phase)
        val recorded = state.snapshot!!.exercises.first().sets.single()
        assertEquals(8, recorded.reps)
        assertEquals(60.0, recorded.weightKg!!, 0.001)
    }

    /**
     * The countdown is derived from an absolute deadline, so advancing the clock
     * without any recomposition still reduces it. A decrementing integer would
     * be unchanged here, which is the drift §10 forbids.
     */
    @Test
    fun `rest remaining is recomputed from the deadline, not counted down`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        viewModel.onCompleteSet(null, null, null)
        testScheduler.advanceUntilIdle()

        val atStart = state.snapshot!!.restRemaining(time.elapsedRealtime())!!
        time.advance(30_000)
        val later = state.snapshot!!.restRemaining(time.elapsedRealtime())!!

        assertEquals(REST_MS, atStart)
        assertEquals(REST_MS - 30_000, later)
    }

    /**
     * The tick is what tells the engine rest is over, and it is a plain call
     * rather than a loop precisely so this test can make it happen.
     */
    @Test
    fun `rest ending moves on to the next set`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        viewModel.onCompleteSet(null, null, null)
        testScheduler.advanceUntilIdle()
        assertEquals(SessionPhase.RESTING, state.phase)

        viewModel.onTick()
        assertEquals("Rest has not run out yet", SessionPhase.RESTING, state.phase)

        time.advance(REST_MS)
        viewModel.onTick()
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.ACTIVE, state.phase)
        assertEquals("Should be on the second set now", 2, state.setNumber)
    }

    @Test
    fun `ticking outside rest does nothing`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        val writes = sessions.persisted.size

        viewModel.onTick()
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.ACTIVE, state.phase)
        assertEquals(writes, sessions.persisted.size)
    }

    @Test
    fun `pausing and resuming returns to the phase it suspended`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        viewModel.onCompleteSet(null, null, null)
        testScheduler.advanceUntilIdle()
        assertEquals(SessionPhase.RESTING, state.phase)

        viewModel.onPause()
        testScheduler.advanceUntilIdle()
        assertEquals(SessionPhase.PAUSED, state.phase)

        viewModel.onResume()
        testScheduler.advanceUntilIdle()
        assertEquals(
            "Resuming during rest must not drop the user into a set they already did",
            SessionPhase.RESTING,
            state.phase,
        )
    }

    /**
     * §10 says a duplicate command returns the current state instead of applying
     * twice. Persisting an unchanged snapshot would still be a transaction, so
     * "harmless" has to mean no write either.
     */
    @Test
    fun `a command that changes nothing is not written down`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        val writes = sessions.persisted.size

        // Resuming a workout that is not paused is rejected by the engine.
        viewModel.onResume()
        testScheduler.advanceUntilIdle()

        assertEquals(writes, sessions.persisted.size)
    }

    @Test
    fun `an active session is restored on cold start`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        val running = sessions.persisted.last()

        val revived = SessionViewModel(
            SessionController(sessions, FakeTemplates(), time),
            FakeExercises(),
            FakeTemplates(),
            preferences,
            downloader,
        )
        testScheduler.advanceUntilIdle()

        assertNotNull(revived.uiState.value.snapshot)
        assertEquals(running.sessionId, revived.uiState.value.snapshot?.sessionId)
    }

    @Test
    fun `finishing the last set completes rather than resting`() = runTest(dispatcher) {
        viewModel.start(SHORT_TEMPLATE_ID)
        testScheduler.advanceUntilIdle()

        viewModel.onCompleteSet(null, null, null)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.COMPLETING, state.phase)

        viewModel.onFinish()
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.COMPLETED, state.phase)
        assertTrue(state.finished)
    }

    /**
     * The screen stops offering it, but the engine must still accept it: a
     * command can arrive from the watch, and §10 makes abandon reachable from
     * every non-terminal state.
     */
    @Test
    fun `abandoning is still possible from completing, even though the screen hides it`() =
        runTest(dispatcher) {
            viewModel.start(SHORT_TEMPLATE_ID)
            testScheduler.advanceUntilIdle()
            viewModel.onCompleteSet(null, null, null)
            testScheduler.advanceUntilIdle()
            assertEquals(SessionPhase.COMPLETING, state.phase)

            viewModel.onAbandon()
            testScheduler.advanceUntilIdle()

            assertEquals(SessionPhase.ABANDONED, state.phase)
        }

    /**
     * Found on a device: after ending a workout early, Start did nothing — the
     * screen opened and bounced straight back to Plans.
     *
     * The controller is a singleton, so the abandoned snapshot stayed in it. A
     * newly opened screen restored that snapshot, saw a terminal phase, decided
     * the workout had just finished, and navigated away before anything could
     * start. A finished session is history, not the active one.
     */
    @Test
    fun `a new workout can be started after abandoning the last one`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        viewModel.onAbandon()
        testScheduler.advanceUntilIdle()
        assertEquals(SessionPhase.ABANDONED, state.phase)

        // The screen is gone and comes back, as navigation would rebuild it.
        val next = SessionViewModel(controller, FakeExercises(), FakeTemplates(), preferences, downloader)
        testScheduler.advanceUntilIdle()
        assertEquals(
            "A finished session must not be restored as the running one",
            null,
            next.uiState.value.snapshot,
        )

        next.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.ACTIVE, next.uiState.value.phase)
    }

    @Test
    fun `a new workout can be started after finishing one`() = runTest(dispatcher) {
        viewModel.start(SHORT_TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        viewModel.onCompleteSet(null, null, null)
        testScheduler.advanceUntilIdle()
        viewModel.onFinish()
        testScheduler.advanceUntilIdle()
        assertEquals(SessionPhase.COMPLETED, state.phase)

        val next = SessionViewModel(controller, FakeExercises(), FakeTemplates(), preferences, downloader)
        testScheduler.advanceUntilIdle()
        next.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.ACTIVE, next.uiState.value.phase)
    }

    @Test
    fun `abandoning keeps the sets already performed`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()
        viewModel.onCompleteSet(reps = 5, weightKg = null, durationMs = null)
        testScheduler.advanceUntilIdle()

        viewModel.onAbandon()
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.ABANDONED, state.phase)
        assertEquals(
            "Abandoning is not deleting; a set that happened still happened",
            1,
            state.snapshot!!.exercises.first().sets.size,
        )
    }

    @Test
    fun `next up preview reflects next set of same exercise during rest and next exercise on last set`() = runTest(dispatcher) {
        viewModel.start(TEMPLATE_ID)
        testScheduler.advanceUntilIdle()

        // Complete set 1 of 3 (Bench Press) -> enters RESTING
        viewModel.onCompleteSet(reps = 8, weightKg = 80.0, durationMs = null)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.RESTING, state.phase)
        val preview = state.nextUpPreview
        assertNotNull(preview)
        assertEquals("Bench press", preview?.name)
        assertEquals(2, preview?.nextSetNumber)
        assertEquals(3, preview?.totalSets)

        // Skip rest to go to set 2
        viewModel.onSkipRest()
        testScheduler.advanceUntilIdle()

        // Complete set 2 of 3 (Bench Press)
        viewModel.onCompleteSet(reps = 8, weightKg = 80.0, durationMs = null)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.RESTING, state.phase)
        assertEquals("Bench press", state.nextUpPreview?.name)
        assertEquals(3, state.nextUpPreview?.nextSetNumber)

        // Skip rest to go to set 3 (last set of Bench Press)
        viewModel.onSkipRest()
        testScheduler.advanceUntilIdle()

        // Complete set 3 of 3 (Bench Press) -> next is Row
        viewModel.onCompleteSet(reps = 8, weightKg = 80.0, durationMs = null)
        testScheduler.advanceUntilIdle()

        assertEquals(SessionPhase.RESTING, state.phase)
        assertEquals("Row", state.nextUpPreview?.name)
        assertEquals(1, state.nextUpPreview?.nextSetNumber)
    }
}

private const val TEMPLATE_ID = "template-1"
private const val SHORT_TEMPLATE_ID = "template-short"
private const val REST_MS = 90_000L

private class RecordingSessionRepository : SessionRepository {
    val persisted = mutableListOf<SessionSnapshot>()
    private val active = MutableStateFlow<SessionSnapshot?>(null)

    override fun observeActive(): Flow<SessionSnapshot?> = active

    override suspend fun restoreActive(): SessionSnapshot? =
        persisted.lastOrNull()?.takeIf { !it.phase.isTerminal }

    override fun observeFinished(): Flow<List<SessionSnapshot>> = emptyFlow()

    override suspend fun persist(snapshot: SessionSnapshot) {
        persisted += snapshot
        active.value = snapshot.takeIf { !it.phase.isTerminal }
    }

    override suspend fun deleteAll() = persisted.clear()
}

private class FakeTemplates : TemplateRepository {
    override fun observeAll(): Flow<List<WorkoutTemplate>> = emptyFlow()

    override suspend fun find(id: String): WorkoutTemplate? = when (id) {
        TEMPLATE_ID -> template(sets = 3, exercises = 2)
        SHORT_TEMPLATE_ID -> template(sets = 1, exercises = 1)
        else -> null
    }

    override suspend fun save(template: WorkoutTemplate) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun deleteAll() = Unit

    private fun template(sets: Int, exercises: Int) = WorkoutTemplate(
        id = TEMPLATE_ID,
        name = "Push day",
        source = PlanSource.MANUAL,
        exercises = (0 until exercises).map { index ->
            PlannedExercise(
                id = "row-$index",
                exerciseId = ExerciseId("ex-$index"),
                position = index,
                target = ExerciseTarget.Reps(sets, 10),
                restMs = REST_MS,
            )
        },
    )
}

private class FakeExercises : ExerciseRepository {
    override suspend fun count(): Int = 0

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> = emptyFlow()

    override suspend fun find(id: ExerciseId): Exercise? = null

    override suspend fun candidates(): List<ExerciseCandidate> = emptyList()

    override suspend fun summaries(
        ids: Collection<ExerciseId>,
    ): Map<ExerciseId, ExerciseSummary> = ids.associateWith { id ->
        ExerciseSummary(
            id = id,
            name = if (id.value == "ex-0") "Bench press" else "Row",
            bodyPart = BodyPart.entries.first(),
            target = Muscle.entries.first(),
            equipment = Equipment.BARBELL,
        )
    }
}

private class FakeMediaDownloader : MediaDownloader {
    val prefetched = mutableListOf<MediaPrefetchRequest>()

    override suspend fun prefetch(items: List<MediaPrefetchRequest>) {
        prefetched += items
    }

    override suspend fun download(
        mediaVersion: Int,
        exerciseId: String,
        mediaType: String,
        mediaRef: MediaRef,
        forceAllowCellular: Boolean,
    ): Result<File> = Result.failure(UnsupportedOperationException())
}
