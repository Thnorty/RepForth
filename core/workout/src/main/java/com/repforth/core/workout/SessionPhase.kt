package com.repforth.core.workout

/**
 * The states in §10's diagram, exactly.
 *
 * Kept as a flat enum rather than a sealed hierarchy because it is persisted as
 * a column and read by the watch protocol in Phase 4: a name that survives a
 * database round-trip and a wire format is worth more here than the extra
 * type-safety of per-state payloads, which [SessionSnapshot] carries instead.
 */
enum class SessionPhase {
    /** Nothing running. */
    IDLE,

    /** A session exists but the first exercise has not begun. */
    PREPARING,

    /** Working a set. */
    ACTIVE,

    /** Between sets, counting down. */
    RESTING,

    /** Suspended. [SessionSnapshot.phaseBeforePause] says what to return to. */
    PAUSED,

    /** Final set done; the summary is being written. */
    COMPLETING,

    /** Finished and recorded. Terminal. */
    COMPLETED,

    /** Given up on. Terminal, and distinct from completed on purpose. */
    ABANDONED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == ABANDONED

    /** §10: abandon is reachable from every non-terminal state after preparing. */
    val canAbandon: Boolean get() = this == ACTIVE || this == RESTING || this == PAUSED
}
