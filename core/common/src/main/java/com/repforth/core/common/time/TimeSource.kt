package com.repforth.core.common.time

/**
 * The two clocks this app needs, kept apart on purpose.
 *
 * §10 requires a countdown to be derived from a monotonic deadline rather than a
 * decrementing counter, and the reason the two are separate here is that they
 * fail differently:
 *
 * - [now] is wall-clock time. It is what a record means when it says it happened
 *   at 19:04, and it is the only one worth persisting. It can also jump — a
 *   timezone change, an NTP correction, the user setting the clock — so a
 *   countdown driven from it can skip a minute or run backwards.
 * - [elapsedRealtime] only ever moves forward, at one millisecond per
 *   millisecond, including while the device is asleep. It is meaningless across
 *   a reboot, so it must never be stored; it is what a running timer counts.
 *
 * Injected rather than called statically so the whole workout engine can be
 * tested against a fake clock, with no device and no waiting.
 */
interface TimeSource {

    /** Milliseconds since the Unix epoch, UTC. Persist this. */
    fun now(): Long

    /** Milliseconds since boot, monotonic. Time a countdown with this; never store it. */
    fun elapsedRealtime(): Long
}
