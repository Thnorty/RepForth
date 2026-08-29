package com.repforth.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.common.time.TimeSource
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.workout.CommandResult
import com.repforth.core.workout.SessionCommand
import com.repforth.core.workout.SessionEngine
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the running workout screen draws. */
data class SessionUiState(
    val snapshot: SessionSnapshot? = null,
    /** Exercise names, resolved once per session rather than per frame. */
    val names: Map<String, String> = emptyMap(),
    /** Rest left, recomputed on a tick rather than counted down. */
    val restRemainingMs: Long? = null,
    val loading: Boolean = true,
    val finished: Boolean = false,
) {
    val phase: SessionPhase? get() = snapshot?.phase
    val currentName: String?
        get() = snapshot?.currentExercise?.let { names[it.exerciseId.value] ?: it.exerciseId.value }

    val setNumber: Int get() = (snapshot?.currentSetIndex ?: 0) + 1
    val setTotal: Int get() = snapshot?.currentExercise?.target?.sets ?: 0
    val exerciseNumber: Int get() = (snapshot?.currentExerciseIndex ?: 0) + 1
    val exerciseTotal: Int get() = snapshot?.exercises?.size ?: 0

    val target: ExerciseTarget? get() = snapshot?.currentExercise?.target
    val isResting: Boolean get() = phase == SessionPhase.RESTING
    val isPaused: Boolean get() = phase == SessionPhase.PAUSED
    val isCompleting: Boolean get() = phase == SessionPhase.COMPLETING
    val isActive: Boolean get() = phase == SessionPhase.ACTIVE
}

/**
 * Drives the workout state machine and writes down what it says (§10).
 *
 * The engine is pure and holds nothing, so this is the piece that gives it a
 * clock and a database. Two rules from §10 shape it:
 *
 * Every applied transition is persisted before anything else acts on it. The
 * snapshot is written first and the UI state updated after, so a process death
 * between them loses a frame rather than a set.
 *
 * The rest timer is not a countdown. The engine holds an absolute monotonic
 * deadline and this recomputes the remainder on a tick, so a recomposition, a
 * slow frame, or a few seconds of the process being frozen cannot make the
 * clock drift.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val templates: TemplateRepository,
    private val exercises: ExerciseRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val engine = SessionEngine(time)
    private val _uiState = MutableStateFlow(SessionUiState())

    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = sessions.restoreActive()
            if (restored != null) {
                adopt(restored)
            } else {
                _uiState.value = SessionUiState(loading = false)
            }
        }
    }

    /**
     * Starts a workout from a plan, unless one is already running.
     *
     * At most one session is active at a time, which the repository also
     * enforces. Refusing here rather than replacing means tapping Start twice,
     * or returning to the screen, cannot discard a workout in progress.
     */
    fun start(templateId: String) {
        if (_uiState.value.snapshot?.phase?.isTerminal == false) return
        viewModelScope.launch {
            val template = templates.find(templateId) ?: return@launch
            val snapshot = engine.start(UUID.randomUUID().toString(), template)
            sessions.persist(snapshot)
            adopt(snapshot)
            dispatch(SessionCommand.Begin(newCommandId()))
        }
    }

    fun onCompleteSet(reps: Int?, weightKg: Double?, durationMs: Long?) = dispatch(
        SessionCommand.CompleteSet(
            commandId = newCommandId(),
            reps = reps,
            weightKg = weightKg,
            durationMs = durationMs,
        ),
    )

    fun onSkipSet() = dispatch(SessionCommand.SkipSet(newCommandId()))

    fun onSkipRest() = dispatch(SessionCommand.SkipRest(newCommandId()))

    fun onNextExercise() = dispatch(SessionCommand.NextExercise(newCommandId()))

    fun onPause() = dispatch(SessionCommand.Pause(newCommandId()))

    fun onResume() = dispatch(SessionCommand.Resume(newCommandId()))

    fun onFinish() = dispatch(SessionCommand.Finish(newCommandId()))

    fun onAbandon() = dispatch(SessionCommand.Abandon(newCommandId()))

    /**
     * Applies a command and writes the result down.
     *
     * A rejected or duplicate command changes nothing and is not persisted:
     * writing an unchanged snapshot would bump nothing but would still be a
     * transaction, and §10's idempotency guarantee is that a replay is
     * *harmless*, not that it is recorded.
     */
    private fun dispatch(command: SessionCommand) {
        val current = _uiState.value.snapshot ?: return
        when (val result = engine.apply(current, command)) {
            is CommandResult.Applied -> viewModelScope.launch {
                sessions.persist(result.state)
                adopt(result.state)
            }

            is CommandResult.Unchanged, is CommandResult.Rejected -> Unit
        }
    }

    private suspend fun adopt(snapshot: SessionSnapshot) {
        val names = if (_uiState.value.names.isEmpty()) {
            exercises.summaries(snapshot.exercises.map { it.exerciseId })
                .entries.associate { (id, summary) -> id.value to summary.name }
        } else {
            _uiState.value.names
        }
        _uiState.value = _uiState.value.copy(
            snapshot = snapshot,
            names = names,
            restRemainingMs = snapshot.restRemaining(time.elapsedRealtime()),
            loading = false,
            finished = snapshot.phase == SessionPhase.COMPLETED ||
                snapshot.phase == SessionPhase.ABANDONED,
        )
    }

    /**
     * Refreshes the rest remainder, and tells the engine when it reaches zero.
     *
     * Called by the screen on a timer rather than looped here. The engine has no
     * clock to notice with — it is a pure function — so someone has to watch and
     * say so, and putting the loop in the ViewModel made it unbounded: a test's
     * virtual clock never went idle and the first test written against it hung
     * instead of failing. A plain function is also directly testable, which a
     * loop is not.
     *
     * The consequence is that rest does not advance while nothing is watching.
     * That is exactly the gap §10's foreground service exists to close, and it
     * is not built yet — recorded in docs/PLAN.md rather than papered over.
     */
    fun onTick() {
        val snapshot = _uiState.value.snapshot ?: return
        if (snapshot.phase != SessionPhase.RESTING) return

        val remaining = snapshot.restRemaining(time.elapsedRealtime())
        _uiState.value = _uiState.value.copy(restRemainingMs = remaining)
        if (remaining != null && remaining <= 0L) {
            dispatch(SessionCommand.RestElapsed(newCommandId()))
        }
    }

    private fun newCommandId() = UUID.randomUUID().toString()

    internal companion object {
        /**
         * Coarse on purpose: a countdown is read, not measured, and waking four
         * times a second to redraw a number that changes once a second is a
         * battery cost during the one activity where the screen stays on
         * longest.
         */
        const val TICK_MS = 500L
    }
}
