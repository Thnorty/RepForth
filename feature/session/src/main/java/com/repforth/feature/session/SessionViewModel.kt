package com.repforth.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.media.download.MediaDownloader
import com.repforth.core.media.download.MediaPrefetchRequest
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.workout.SessionCommand
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the running workout screen draws. */
data class SessionUiState(
    val snapshot: SessionSnapshot? = null,
    /** Exercise summaries, resolved once per session rather than per frame. */
    val summaries: Map<String, ExerciseSummary> = emptyMap(),
    val currentExercise: Exercise? = null,
    val reducedMotion: Boolean = false,
    /** Rest left, recomputed on a tick rather than counted down. */
    val restRemainingMs: Long? = null,
    val loading: Boolean = true,
    val finished: Boolean = false,
) {
    val phase: SessionPhase? get() = snapshot?.phase
    val currentName: String?
        get() = currentExercise?.name ?: snapshot?.currentExercise?.let { summaries[it.exerciseId.value]?.name ?: it.exerciseId.value }

    val nextExerciseSummary: ExerciseSummary?
        get() {
            val currIdx = snapshot?.currentExerciseIndex ?: return null
            val nextPlanned = snapshot.exercises.getOrNull(currIdx + 1) ?: return null
            return summaries[nextPlanned.exerciseId.value]
        }

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
 * The running workout screen's half of the session.
 *
 * The state machine, the clock and the database belong to [SessionController],
 * which the foreground service shares. This turns snapshots into something
 * drawable and taps into commands, and owns nothing that could disagree with the
 * service while both are running.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val controller: SessionController,
    private val exercises: ExerciseRepository,
    private val preferences: UserPreferencesDataSource,
    private val mediaDownloader: MediaDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(reducedMotion = prefs.reducedMotion)
            }
        }
        viewModelScope.launch {
            val restored = controller.restore()
            if (restored != null) {
                adopt(restored)
            } else {
                _uiState.value = _uiState.value.copy(loading = false)
            }

            // Whatever the service does arrives here, so a rest that ended while
            // the app was in the background is already applied by the time the
            // screen is looked at again.
            controller.state.collect { snapshot ->
                if (snapshot != null) adopt(snapshot)
            }
        }
    }

    fun start(templateId: String) {
        viewModelScope.launch {
            val started = controller.start(templateId) ?: return@launch
            adopt(started)
            controller.dispatch(SessionCommand.Begin(controller.newCommandId()))
        }
    }

    fun onCompleteSet(reps: Int?, weightKg: Double?, durationMs: Long?) = dispatch(
        SessionCommand.CompleteSet(
            commandId = controller.newCommandId(),
            reps = reps,
            weightKg = weightKg,
            durationMs = durationMs,
        ),
    )

    fun onSkipSet() = dispatch(SessionCommand.SkipSet(controller.newCommandId()))

    fun onSkipRest() = dispatch(SessionCommand.SkipRest(controller.newCommandId()))

    fun onNextExercise() = dispatch(SessionCommand.NextExercise(controller.newCommandId()))

    fun onPause() = dispatch(SessionCommand.Pause(controller.newCommandId()))

    fun onResume() = dispatch(SessionCommand.Resume(controller.newCommandId()))

    fun onFinish() = dispatch(SessionCommand.Finish(controller.newCommandId()))

    fun onAbandon() = dispatch(SessionCommand.Abandon(controller.newCommandId()))

    /**
     * Refreshes the rest remainder, and lets the controller end the rest.
     */
    fun onTick() {
        viewModelScope.launch {
            controller.onRestTick()
            val remaining = controller.restRemaining()
            if (remaining != _uiState.value.restRemainingMs) {
                _uiState.value = _uiState.value.copy(restRemainingMs = remaining)
            }
        }
    }

    /**
     * The returned snapshot is adopted directly, including a terminal one.
     *
     * The controller drops a finished session from its state, so the terminal
     * snapshot never arrives through the flow — and this screen still has to
     * learn that the workout ended in order to leave.
     */
    private fun dispatch(command: SessionCommand) {
        viewModelScope.launch { controller.dispatch(command)?.let { adopt(it) } }
    }

    private suspend fun adopt(snapshot: SessionSnapshot) {
        val summaries = _uiState.value.summaries.ifEmpty {
            exercises.summaries(snapshot.exercises.map { it.exerciseId })
                .entries.associate { (id, summary) -> id.value to summary }
        }
        val current = snapshot.currentExercise?.let { exercises.find(it.exerciseId) }

        // Prefetch current and upcoming 2 exercises
        val currentIndex = snapshot.currentExerciseIndex ?: 0
        val upcoming = snapshot.exercises.drop(currentIndex).take(3)
        val prefetchRequests = upcoming.flatMap { planned ->
            val summary = summaries[planned.exerciseId.value]
            listOfNotNull(
                summary?.thumbnail?.takeIf { it.isAvailable }?.let {
                    MediaPrefetchRequest(exerciseId = planned.exerciseId.value, mediaType = "thumbnail", mediaRef = it)
                },
            )
        }
        if (prefetchRequests.isNotEmpty()) {
            viewModelScope.launch {
                mediaDownloader.prefetch(prefetchRequests)
            }
        }

        _uiState.value = _uiState.value.copy(
            snapshot = snapshot,
            summaries = summaries,
            currentExercise = current,
            restRemainingMs = controller.restRemaining(),
            loading = false,
            finished = snapshot.phase.isTerminal,
        )
    }

    internal companion object {
        const val TICK_MS = 500L
    }
}
