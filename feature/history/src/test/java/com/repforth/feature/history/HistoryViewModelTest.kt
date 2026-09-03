package com.repforth.feature.history

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
import com.repforth.core.model.UserProfile
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import com.repforth.core.workout.SetOutcome
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Progress tab's wiring.
 *
 * The arithmetic is tested in `SessionStatisticsTest`; what is untested until
 * here is the part that could still be wrong with correct arithmetic — the
 * order the history is shown in, and what happens to an exercise the catalog no
 * longer has.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Europe/Istanbul")
    private lateinit var sessions: FakeSessions

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sessions = FakeSessions()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        exercises: ExerciseRepository = FakeExercises(),
        profile: UserProfile? = null,
    ) = HistoryViewModel(
        sessions,
        FakeProfiles(profile),
        exercises,
        FakeTimeSource(),
        zone,
    )

    /**
     * Collecting is required: the state is a `WhileSubscribed` flow, so its
     * value stays at the initial one until something is watching.
     */
    private suspend fun stateOf(vm: HistoryViewModel): HistoryUiState =
        vm.uiState.first { !it.loading }

    @Test
    fun `an empty history reports itself as empty`() = runTest(dispatcher) {
        val state = stateOf(viewModel())

        assertTrue(state.isEmpty)
        assertEquals(0, state.progress.workouts)
        assertTrue(state.mostPerformed.isEmpty())
    }

    @Test
    fun `the history is newest first`() = runTest(dispatcher) {
        sessions.emit(
            listOf(
                session("old", startedAt = 1_000),
                session("newest", startedAt = 9_000),
                session("middle", startedAt = 5_000),
            ),
        )

        val state = stateOf(viewModel())

        assertEquals(
            "A history answers what did I do last",
            listOf("newest", "middle", "old"),
            state.workouts.map { it.sessionId },
        )
        assertFalse(state.isEmpty)
    }

    @Test
    fun `most performed comes back as names, in rank order`() = runTest(dispatcher) {
        sessions.emit(
            listOf(
                session("s1", 1_000, exerciseId = "bench"),
                session("s2", 2_000, exerciseId = "bench"),
                session("s3", 3_000, exerciseId = "squat"),
            ),
        )

        val state = stateOf(viewModel())

        assertEquals(listOf("Bench press", "Squat"), state.mostPerformed)
    }

    /**
     * A dataset update can remove an exercise a history references. In a
     * "most performed" summary the id is no use to anyone, so the entry is
     * dropped — the session itself still holds it.
     */
    @Test
    fun `an exercise the catalog no longer has is left out of the summary`() =
        runTest(dispatcher) {
            sessions.emit(
                listOf(
                    session("s1", 1_000, exerciseId = "vanished"),
                    session("s2", 2_000, exerciseId = "squat"),
                ),
            )

            val state = stateOf(viewModel())

            assertEquals(listOf("Squat"), state.mostPerformed)
            assertEquals("The workout itself is still listed", 2, state.workouts.size)
        }

    /**
     * Abandoning keeps the sets that were performed, and history that hides
     * them makes that promise untrue. The query behind this returned only
     * COMPLETED, so an ended-early workout was in the database and nowhere on
     * screen — which is what "the sets are kept" was quietly failing to mean.
     */
    @Test
    fun `a workout that ended early is listed, and marked as not completed`() =
        runTest(dispatcher) {
            sessions.emit(
                listOf(
                    session("finished", 1_000),
                    session("ended-early", 2_000, phase = SessionPhase.ABANDONED),
                ),
            )

            val state = stateOf(viewModel())

            assertEquals(2, state.workouts.size)
            assertEquals(2, state.progress.workouts)

            val early = state.workouts.first { it.sessionId == "ended-early" }
            assertFalse("It should be labelled, not hidden", early.completed)
            assertEquals("Its sets still count", 1, early.setsCompleted)
        }

    @Test
    fun `totals reach the screen`() = runTest(dispatcher) {
        sessions.emit(
            listOf(
                session("s1", 1_000, sets = listOf(completed(0, 10, 60.0))),
                session("s2", 2_000, sets = listOf(completed(0, 5, 100.0), skipped(1))),
            ),
        )

        val state = stateOf(viewModel())

        assertEquals(2, state.progress.workouts)
        assertEquals(1100.0, state.progress.totalVolumeKg, 0.001)
        assertEquals(2, state.progress.totalSets)
    }

    private fun completed(position: Int, reps: Int, weight: Double) =
        SetOutcome(position, skipped = false, reps = reps, weightKg = weight, recordedAt = 0)

    private fun skipped(position: Int) = SetOutcome(position, skipped = true, recordedAt = 0)

    private fun session(
        id: String,
        startedAt: Long,
        phase: SessionPhase = SessionPhase.COMPLETED,
        exerciseId: String = "bench",
        sets: List<SetOutcome> = listOf(SetOutcome(0, false, reps = 10, weightKg = 50.0, recordedAt = 0)),
    ) = SessionSnapshot(
        sessionId = id,
        templateId = null,
        phase = phase,
        exercises = listOf(
            SessionExercise(
                id = "se-$id",
                exerciseId = ExerciseId(exerciseId),
                position = 0,
                target = ExerciseTarget.Reps(3, 10),
                restMs = 60_000,
                sets = sets,
            ),
        ),
        startedAt = startedAt,
        endedAt = startedAt + 60_000,
    )
}

private class FakeSessions : SessionRepository {
    private val completed = MutableStateFlow<List<SessionSnapshot>>(emptyList())

    fun emit(list: List<SessionSnapshot>) {
        completed.value = list
    }

    override fun observeActive(): Flow<SessionSnapshot?> = MutableStateFlow(null)

    override suspend fun restoreActive(): SessionSnapshot? = null

    override fun observeFinished(): Flow<List<SessionSnapshot>> = completed

    override suspend fun persist(snapshot: SessionSnapshot) = Unit

    override suspend fun deleteAll() = Unit
}

private class FakeExercises : ExerciseRepository {
    override suspend fun count(): Int = 0

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> = emptyFlow()

    override suspend fun find(id: ExerciseId): Exercise? = null

    override suspend fun candidates(): List<ExerciseCandidate> = emptyList()

    override suspend fun summaries(
        ids: Collection<ExerciseId>,
    ): Map<ExerciseId, ExerciseSummary> = ids
        .filter { it.value != "vanished" }
        .associateWith { id ->
            ExerciseSummary(
                id = id,
                name = when (id.value) {
                    "bench" -> "Bench press"
                    "squat" -> "Squat"
                    else -> id.value
                },
                bodyPart = BodyPart.entries.first(),
                target = Muscle.entries.first(),
                equipment = Equipment.BARBELL,
            )
        }
}

/**
 * The fifth copy of this in the repo, which is four too many — the other four
 * are in `core:transfer`, `feature:builder`, `feature:home` and
 * `feature:settings`. Consolidating them into `core:testing` is worth doing and
 * is not this change.
 */
private class FakeProfiles(private val profile: UserProfile?) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = flowOf(profile)
    override suspend fun getProfile(): UserProfile? = profile
    override suspend fun save(profile: UserProfile) = Unit
    override suspend fun deleteAll() = Unit
}
