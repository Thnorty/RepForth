package com.repforth.feature.session

import com.repforth.core.common.time.TimeSource
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.workout.CommandResult
import com.repforth.core.workout.SessionCommand
import com.repforth.core.workout.SessionEngine
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one owner of the running workout.
 *
 * A singleton because two things drive the same session: the screen while it is
 * open, and the foreground service while it is not. Two `SessionEngine`
 * instances over one row is a race — both would read the same snapshot, both
 * apply a command to it, and the second write would silently discard the first.
 * §10's idempotency makes a *duplicate* harmless; it does nothing about two
 * different commands applied to the same stale state.
 *
 * So the engine lives here, behind a mutex, and both callers ask this instead.
 */
@Singleton
class SessionController @Inject constructor(
    private val sessions: SessionRepository,
    private val templates: TemplateRepository,
    private val time: TimeSource,
) {
    private val engine = SessionEngine(time)
    private val mutex = Mutex()

    /**
     * The session in progress, or null.
     *
     * Null the moment a session ends. A finished workout is history, and leaving
     * it here made a newly opened screen restore it, conclude the workout had
     * just ended, and navigate away before a new one could start — so Start
     * appeared to do nothing after ending a workout early.
     *
     * The terminal snapshot is still returned by [dispatch], which is how the
     * screen that was watching learns it finished.
     */
    private val _state = MutableStateFlow<SessionSnapshot?>(null)
    val state: StateFlow<SessionSnapshot?> = _state.asStateFlow()

    private var restored = false

    /** Reads whatever was running, once per process. */
    suspend fun restore(): SessionSnapshot? = mutex.withLock { restoreLocked() }

    /**
     * [restore] without taking the lock, for callers that already hold it.
     *
     * The mutex is not reentrant, so [start] cannot simply call [restore] — and
     * it has to do the equivalent, for the reason on [start].
     */
    private suspend fun restoreLocked(): SessionSnapshot? {
        if (!restored) {
            restored = true
            _state.value = sessions.restoreActive()?.takeIf { !it.phase.isTerminal }
        }
        return _state.value
    }

    /**
     * Begin [templateId], unless something is already running.
     *
     * **This used to return whatever was already in progress**, whichever plan
     * had been asked for. Tapping "start" on a plan then silently resumed a
     * different one — often a session left unfinished days earlier, since an
     * active session never expires — and the screen would show a workout the
     * user had not chosen, mid-way through, with no explanation.
     *
     * The outcome is now something the caller has to look at. Resuming the same
     * plan is fine and is not a conflict; being handed a *different* one is the
     * bug, and it is the caller's job to ask what the user wants.
     *
     * **It restores first, rather than trusting whatever is in memory.**
     * Otherwise the answer depends on whether some other caller happened to
     * have called [restore] already: on a cold start, a workout sitting in the
     * database but not yet read would leave `_state` null, and this would
     * cheerfully begin a second session on top of it. Two coroutines racing to
     * open the same screen is exactly the situation that produces that, and it
     * is not a race anything else here could arbitrate.
     */
    suspend fun start(templateId: String): StartOutcome = mutex.withLock {
        val current = restoreLocked()

        if (current != null && !current.phase.isTerminal) {
            return@withLock if (current.templateId == templateId) {
                StartOutcome.Resumed(current)
            } else {
                StartOutcome.Blocked(current)
            }
        }

        val template = templates.find(templateId) ?: return@withLock StartOutcome.NoSuchPlan
        val started = engine.start(UUID.randomUUID().toString(), template)
        sessions.persist(started)
        _state.value = started
        StartOutcome.Started(started)
    }

    /**
     * Give up whatever is running and begin [templateId].
     *
     * Only reached from a user answering the conflict — never automatically.
     * Everything already recorded on the abandoned session is kept, which is
     * what `Abandon` means in §10.
     */
    suspend fun abandonAndStart(templateId: String): StartOutcome {
        _state.value?.takeIf { !it.phase.isTerminal }?.let {
            dispatch(SessionCommand.Abandon(newCommandId()))
        }
        return start(templateId)
    }

    /**
     * Applies a command, persisting before anything observes the result (§10).
     *
     * A rejected or duplicate command is not written: a replay being harmless
     * has to mean no transaction either, not merely no state change.
     */
    suspend fun dispatch(command: SessionCommand): SessionSnapshot? = mutex.withLock {
        val current = _state.value ?: return@withLock null
        when (val result = engine.apply(current, command)) {
            is CommandResult.Applied -> {
                sessions.persist(result.state)
                // Persist first, then stop calling it active. The row is written
                // either way; what changes is whether anything still treats it
                // as the workout in progress.
                _state.value = result.state.takeIf { !it.phase.isTerminal }
                result.state
            }

            is CommandResult.Unchanged, is CommandResult.Rejected -> current
        }
    }

    /**
     * Tells the engine when rest has run out.
     *
     * Whoever is watching the clock calls this — the screen while it is open,
     * the service otherwise. Both calling is fine: the second finds the phase is
     * no longer `RESTING` and is rejected without a write.
     */
    suspend fun onRestTick(): SessionSnapshot? {
        val snapshot = _state.value ?: return null
        if (snapshot.phase != SessionPhase.RESTING) return snapshot

        val remaining = snapshot.restRemaining(time.elapsedRealtime())
        return if (remaining != null && remaining <= 0L) {
            dispatch(SessionCommand.RestElapsed(newCommandId()))
        } else {
            snapshot
        }
    }

    /** Milliseconds of rest left right now, or null when not resting. */
    fun restRemaining(): Long? = _state.value?.restRemaining(time.elapsedRealtime())

    fun newCommandId(): String = UUID.randomUUID().toString()
}

/** What [SessionController.start] did, which the caller has to decide about. */
sealed interface StartOutcome {

    /** A new session began. */
    data class Started(val snapshot: SessionSnapshot) : StartOutcome

    /** The requested plan was already running; this is that session. */
    data class Resumed(val snapshot: SessionSnapshot) : StartOutcome

    /**
     * A *different* workout is in progress and was left alone.
     *
     * Not an error, and not something to resolve automatically: discarding
     * someone's half-finished workout to honour a tap is worse than asking.
     */
    data class Blocked(val running: SessionSnapshot) : StartOutcome

    /** The plan is gone. */
    data object NoSuchPlan : StartOutcome
}
