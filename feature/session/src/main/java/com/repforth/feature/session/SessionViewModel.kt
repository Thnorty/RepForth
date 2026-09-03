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
import com.repforth.core.model.MediaRef
import com.repforth.core.workout.SessionCommand
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Preview of what comes next during rest (either next set of current movement or next movement). */
data class NextUpPreview(
    val thumbnail: MediaRef,
    val name: String,
    val nextSetNumber: Int? = null,
    val totalSets: Int? = null,
)

/**
 * How often the rest countdown is recomputed.
 *
 * Internal rather than private because the screen animates the rest ring across
 * exactly this interval — a ring easing over a different span than the one
 * between its updates is what made the countdown move in two visible jerks per
 * second.
 */
internal const val REST_TICK_MS = 500L

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
    /**
     * A workout that was already running when a different plan was started.
     *
     * Non-null means the screen owes the user a question. It is never resolved
     * automatically: silently resuming this was the original bug, and silently
     * discarding it would be a worse one.
     */
    val conflictingSession: SessionSnapshot? = null,
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

    val nextUpPreview: NextUpPreview?
        get() {
            val snap = snapshot ?: return null
            if (snap.isLastSetOfExercise) {
                // Moving to the next exercise in the plan
                val nextPlanned = snap.exercises.getOrNull(snap.currentExerciseIndex + 1) ?: return null
                val summary = summaries[nextPlanned.exerciseId.value] ?: return null
                return NextUpPreview(
                    thumbnail = summary.thumbnail,
                    name = summary.name,
                    nextSetNumber = 1,
                    totalSets = nextPlanned.target.sets,
                )
            } else {
                // Next set of the current exercise
                val currPlanned = snap.currentExercise ?: return null
                val summary = summaries[currPlanned.exerciseId.value]
                val thumbnail = currentExercise?.thumbnail ?: summary?.thumbnail ?: MediaRef.Unavailable
                val name = currentName ?: summary?.name ?: ""
                val nextSetNum = snap.currentSetIndex + 2
                val totalSets = currPlanned.target.sets
                return NextUpPreview(
                    thumbnail = thumbnail,
                    name = name,
                    nextSetNumber = nextSetNum,
                    totalSets = totalSets,
                )
            }
        }

    val setNumber: Int get() = (snapshot?.currentSetIndex ?: 0) + 1
    val setTotal: Int get() = snapshot?.currentExercise?.target?.sets ?: 0
    val exerciseNumber: Int get() = (snapshot?.currentExerciseIndex ?: 0) + 1
    val exerciseTotal: Int get() = snapshot?.exercises?.size ?: 0

    val target: ExerciseTarget? get() = snapshot?.currentExercise?.target
    /**
     * How long this rest was supposed to be, so the countdown can be drawn as a
     * fraction rather than only as a number.
     */
    val restTotalMs: Long? get() = snapshot?.currentExercise?.restMs?.takeIf { it > 0L }

    /**
     * Rest remaining as a fraction of the whole, counting down from 1.
     *
     * Null when there is no rest in progress or the plan asked for none — a
     * ring with nothing to measure is a circle, and a circle drawn for its own
     * sake is exactly the decorative motion §12 rules out on this screen.
     */
    val restFraction: Float? get() {
        val total = restTotalMs ?: return null
        val remaining = restRemainingMs ?: return null
        return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

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

    /** Remembered so the conflict dialog knows what to start if it is answered. */
    private var pendingTemplateId: String? = null

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
        pendingTemplateId = templateId
        viewModelScope.launch { begin(controller.start(templateId)) }
    }

    /** The user chose to discard the running workout and start the one they tapped. */
    fun onDiscardRunningAndStart() {
        val templateId = pendingTemplateId ?: return
        _uiState.value = _uiState.value.copy(conflictingSession = null)
        viewModelScope.launch { begin(controller.abandonAndStart(templateId)) }
    }

    /** The user chose to keep the workout that was already going. */
    fun onKeepRunningSession() {
        val running = _uiState.value.conflictingSession ?: return
        _uiState.value = _uiState.value.copy(conflictingSession = null)
        viewModelScope.launch { adopt(running) }
    }

    private suspend fun begin(outcome: StartOutcome) {
        when (outcome) {
            is StartOutcome.Started -> {
                adopt(outcome.snapshot)
                controller.dispatch(SessionCommand.Begin(controller.newCommandId()))
            }

            // The same plan, already going. Nothing to begin and nothing to ask:
            // this is what the user meant by tapping it.
            is StartOutcome.Resumed -> adopt(outcome.snapshot)

            is StartOutcome.Blocked ->
                _uiState.value = _uiState.value.copy(
                    conflictingSession = outcome.running,
                    loading = false,
                )

            StartOutcome.NoSuchPlan ->
                _uiState.value = _uiState.value.copy(loading = false)
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

    }
}
