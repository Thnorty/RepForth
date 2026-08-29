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

    private val _state = MutableStateFlow<SessionSnapshot?>(null)
    val state: StateFlow<SessionSnapshot?> = _state.asStateFlow()

    private var restored = false

    /** Reads whatever was running, once per process. */
    suspend fun restore(): SessionSnapshot? = mutex.withLock {
        if (!restored) {
            restored = true
            _state.value = sessions.restoreActive()
        }
        _state.value
    }

    /**
     * Starts a workout from a plan, unless one is already running.
     *
     * Refusing rather than replacing: tapping Start twice, or the service and
     * the screen both reacting to the same intent, must not discard a workout
     * in progress.
     */
    suspend fun start(templateId: String): SessionSnapshot? = mutex.withLock {
        val current = _state.value
        if (current != null && !current.phase.isTerminal) return@withLock current

        val template = templates.find(templateId) ?: return@withLock null
        val started = engine.start(UUID.randomUUID().toString(), template)
        sessions.persist(started)
        _state.value = started
        started
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
                _state.value = result.state
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
