package com.repforth.core.ai

import com.repforth.core.ai.http.generationTimeoutSeconds
import com.repforth.core.model.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The generation deadline scales with what is being asked for.
 *
 * Watched failing by making [generationTimeoutSeconds] ignore `days`.
 *
 * This exists because a real device hit it: a six-day request against the
 * 60-second default spent its whole budget on a response roughly six times the
 * size it was chosen for, and reported a timeout as though the provider were at
 * fault. A deadline that does not know the request got bigger is not a deadline,
 * it is a coin toss.
 */
class GenerationTimeoutTest {

    @Test
    fun `a single day still gets exactly the configured budget`() {
        assertEquals(60, generationTimeoutSeconds(60, days = 1))
    }

    @Test
    fun `the budget grows with the number of days`() {
        assertEquals(120, generationTimeoutSeconds(60, days = 2))
        assertEquals(180, generationTimeoutSeconds(60, days = 3))
    }

    /**
     * Never more than the user could have set by hand.
     *
     * Seven days at the default would be 420 seconds — seven minutes of a
     * spinner, and longer than the maximum the settings screen allows anyone to
     * choose deliberately.
     */
    @Test
    fun `the budget is clamped to the settings maximum`() {
        assertEquals(
            ProviderSettings.MAX_TIMEOUT_SECONDS,
            generationTimeoutSeconds(60, days = 7),
        )
        assertEquals(
            ProviderSettings.MAX_TIMEOUT_SECONDS,
            generationTimeoutSeconds(ProviderSettings.MAX_TIMEOUT_SECONDS, days = 2),
        )
    }

    /** A nonsensical day count must not produce a zero-second deadline. */
    @Test
    fun `zero days is treated as one rather than as no time at all`() {
        assertEquals(60, generationTimeoutSeconds(60, days = 0))
    }
}
