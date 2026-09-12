package com.repforth.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.media.download.MediaDownloader
import com.repforth.core.media.download.THUMBNAIL_MEDIA_TYPE
import com.repforth.core.media.download.MediaPrefetchRequest
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.MediaRef
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.model.progressedBy
import com.repforth.core.model.progressionLevels
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

    /**
     * The moving version, for the rest screen's preview.
     *
     * Rest is the one moment in a workout with time to look at something, and
     * the preview used to be a 48dp still beside two lines of text. The screen
     * picks between this and [thumbnail] on the reduced-motion setting, exactly
     * as the active set's own media does.
     */
    val animation: MediaRef = MediaRef.Unavailable,
    val name: String,
    val nextSetNumber: Int? = null,
    val totalSets: Int? = null,

    /**
     * What the next set asks for — repetitions or seconds, and the load.
     *
     * The preview named the exercise and counted the sets and said nothing about
     * the work itself, so "what am I about to lift" was a question the rest
     * screen could answer and did not.
     */
    val target: ExerciseTarget? = null,
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
    /**
     * Time left on the timed set in progress, on the same terms.
     *
     * Null for an ordinary set, which is what tells the screen to draw a Log set
     * button rather than a countdown.
     */
    val setRemainingMs: Long? = null,
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
    /**
     * The plan this workout was performed from, if it had one.
     *
     * Held so the finish screen can offer to move it. Null for a session started
     * from no plan, and the offer simply does not appear -- there is nowhere to
     * write.
     */
    val plan: WorkoutTemplate? = null,
    /**
     * The name of that workout, so the question can name it.
     *
     * Null when the running workout came from no plan; the dialog says
     * something generic rather than leaving a gap in the sentence.
     */
    val conflictingName: String? = null,
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
                    animation = summary.animation,
                    name = summary.name,
                    nextSetNumber = 1,
                    totalSets = nextPlanned.target.sets,
                    target = nextPlanned.target,
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
                    animation = currentExercise?.animation
                        ?: summary?.animation
                        ?: MediaRef.Unavailable,
                    name = name,
                    nextSetNumber = nextSetNum,
                    totalSets = totalSets,
                    // The same exercise, so the same prescription: another set of
                    // what is already on the bar.
                    target = currPlanned.target,
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

    /**
     * A timed set is counting right now.
     *
     * What the screen turns on, rather than the target type: an exercise can be
     * timed while the workout is paused or resting, and neither of those is a
     * moment to draw a running countdown.
     */
    val isTimedSetRunning: Boolean get() = isActive && setRemainingMs != null
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
    private val templates: TemplateRepository,
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
                // ...and begun, if it never was. Reaching the screen without a
                // plan id -- "resume what is running" -- skips `start` entirely,
                // so this is the other door onto a session left in `PREPARING`.
                adoptAndBegin(restored)
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
        _uiState.value = _uiState.value.copy(conflictingSession = null, conflictingName = null)
        viewModelScope.launch { begin(controller.abandonAndStart(templateId)) }
    }

    /** The user chose to keep the workout that was already going. */
    fun onKeepRunningSession() {
        val running = _uiState.value.conflictingSession ?: return
        _uiState.value = _uiState.value.copy(conflictingSession = null, conflictingName = null)
        viewModelScope.launch { adopt(running) }
    }

    private suspend fun begin(outcome: StartOutcome) {
        when (outcome) {
            is StartOutcome.Started -> adoptAndBegin(outcome.snapshot)

            // The same plan, already going -- which is what the user meant by
            // tapping it. It still has to be *begun* if nobody has: see below.
            is StartOutcome.Resumed -> adoptAndBegin(outcome.snapshot)

            is StartOutcome.Blocked ->
                _uiState.value = _uiState.value.copy(
                    conflictingSession = outcome.running,
                    conflictingName = outcome.running.templateId?.let { templates.find(it)?.name },
                    loading = false,
                )

            StartOutcome.NoSuchPlan ->
                _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    /**
     * Adopts a session, and begins it if nobody has.
     *
     * `Resumed` used to adopt and stop there, on the reasoning that a session
     * already going has nothing to begin. That is true of every session anyone
     * had watched -- and false for one created outside this screen.
     *
     * `WorkoutStartViewModel` answers the "a different workout is running"
     * question by calling `abandonAndStart`, which creates the new session in
     * `PREPARING`; `Begin` has only ever been sent from here. The screen then
     * opened on a session with the id it asked for, was told `Resumed`, and left
     * it in `PREPARING` forever. The engine rejects `CompleteSet` there with "no
     * set in progress" and `Pause` with "nothing to pause", and a rejected
     * command returns the state unchanged -- so the workout drew perfectly and
     * every button was dead. Reported from a phone, and invisible from the
     * screen's side because nothing about the screen was wrong.
     *
     * Asking the phase rather than the outcome also covers the other way to get
     * there: the app dying between `start` persisting `PREPARING` and this
     * sending `Begin` leaves the same stuck session, and this now begins it on
     * the next open.
     */
    private suspend fun adoptAndBegin(snapshot: SessionSnapshot) {
        adopt(snapshot)
        if (snapshot.phase == SessionPhase.PREPARING) {
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


    fun onPause() = dispatch(SessionCommand.Pause(controller.newCommandId()))

    fun onResume() = dispatch(SessionCommand.Resume(controller.newCommandId()))

    /**
     * Finishes, carrying §3's note if one was written.
     *
     * The note arrives from the screen rather than being held here, because it
     * exists for the few seconds between typing and pressing Finish and nothing
     * else reads it. Blank is normalised to nothing by the engine.
     */
    fun onFinish(note: String? = null, effort: Int? = null, adjustPlan: Boolean = false) {
        // The plan first, and deliberately: `dispatch` adopts a terminal
        // snapshot, which navigates the screen away. Writing afterwards would
        // race a composable that is being torn down, and the one thing worse
        // than not moving the plan is moving it sometimes.
        if (adjustPlan) {
            val plan = _uiState.value.plan
            val levels = progressionLevels(effort)
            if (plan != null && levels != 0) {
                viewModelScope.launch { templates.save(plan.progressedBy(levels)) }
            }
        }
        dispatch(SessionCommand.Finish(controller.newCommandId(), note = note, effort = effort))
    }

    fun onAbandon() = dispatch(SessionCommand.Abandon(controller.newCommandId()))

    /**
     * Refreshes both countdowns, and lets the controller end whichever ran out.
     *
     * Compared before writing so a tick that changed nothing does not recompose
     * the screen; at two ticks a second for the length of a workout that is not
     * a micro-optimisation.
     */
    fun onTick() {
        viewModelScope.launch {
            controller.onTick()
            val rest = controller.restRemaining()
            val set = controller.setRemaining()
            val current = _uiState.value
            if (rest != current.restRemainingMs || set != current.setRemainingMs) {
                _uiState.value = current.copy(restRemainingMs = rest, setRemainingMs = set)
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
                    MediaPrefetchRequest(
                        exerciseId = planned.exerciseId.value,
                        mediaType = THUMBNAIL_MEDIA_TYPE,
                        mediaRef = it,
                    )
                },
            )
        }
        if (prefetchRequests.isNotEmpty()) {
            viewModelScope.launch {
                mediaDownloader.prefetch(prefetchRequests)
            }
        }

        // Looked up once per session rather than on every snapshot. The finish
        // screen needs it, and a workout cannot change which plan it came from
        // part way through.
        val plan = _uiState.value.plan
            ?: snapshot.templateId?.let { templates.find(it) }

        _uiState.value = _uiState.value.copy(
            snapshot = snapshot,
            summaries = summaries,
            currentExercise = current,
            plan = plan,
            restRemainingMs = controller.restRemaining(),
            setRemainingMs = controller.setRemaining(),
            loading = false,
            finished = snapshot.phase.isTerminal,
        )
    }

    internal companion object {

    }
}
