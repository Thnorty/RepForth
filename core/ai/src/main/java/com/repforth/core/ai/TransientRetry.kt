package com.repforth.core.ai

import kotlinx.coroutines.delay

/** Total tries for one provider call, including the first. */
private const val TRANSIENT_ATTEMPTS = 3
private const val FIRST_BACKOFF_MS = 1_000L

/**
 * One logical provider call, retrying only a server error.
 *
 * This is a different thing from a repair attempt, and conflating the two is
 * what made a provider outage look like a broken app. A repair attempt is for
 * output the model got *wrong*: it costs tokens, it may only happen once (§8),
 * and it needs feedback to be worth making. A 5xx is not wrong output -- it is
 * no output. Gemini answers a demand spike with `503 UNAVAILABLE` and the words
 * "spikes in demand are usually temporary", nothing is billed, and the identical
 * request a second later commonly succeeds.
 *
 * Bounded and backed off rather than persistent: the user is watching a
 * spinner, the card offers cancel, and an app that hammers a provider that just
 * said it is overloaded is part of the problem.
 *
 * Shared by generation and plan edits, so both treat an outage the same way.
 */
internal suspend fun <T> retryingServerErrors(
    isServerError: (T) -> Boolean,
    call: suspend () -> T,
): T {
    var backoffMs = FIRST_BACKOFF_MS
    repeat(TRANSIENT_ATTEMPTS - 1) {
        val result = call()
        if (!isServerError(result)) return result
        delay(backoffMs)
        backoffMs *= 2
    }
    return call()
}
