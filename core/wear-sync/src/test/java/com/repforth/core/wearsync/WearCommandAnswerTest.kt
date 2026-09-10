package com.repforth.core.wearsync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who answers a watch command, and how often the wrist hears about it.
 *
 * **One command produced two publishes, and the second was harmful rather than
 * merely wasteful.** `WearCommandService` published after applying a command and
 * `WorkoutService`'s collector published again on the state change — but only
 * the collector attaches the exercise's picture and §6's notice. The command
 * service's publish therefore carried no asset, and the watch believed it: it
 * dropped the image and re-fetched and re-decoded it milliseconds later, on
 * every single tap. Since the watch started animating that media, each tap threw
 * away a decoded GIF and read it back over the Data Layer.
 *
 * Nothing was *wrong* — the writes are idempotent and the state always converged
 * — which is why it sat on the backlog as "wasteful". It was not wasteful, and
 * the way to have known that was to look at what the two publishes differed by
 * rather than at how many there were.
 */
class WearCommandAnswerTest {

    /**
     * The ordinary case: the engine took it, so the state changed, so the
     * collector will publish it properly. Answering here as well is the bug.
     */
    @Test
    fun `an applied command is answered by the state publish, not here`() {
        assertFalse(wearCommandNeedsDirectAnswer(revisionBefore = 7, revisionAfter = 8))
    }

    /**
     * The engine can refuse a command the protocol admitted — a duplicate id, or
     * a phase that does not allow it. Nothing changes, so no collector fires,
     * and a watch left unanswered sits on a stale snapshot until something else
     * happens to move. This is the case the direct publish exists for.
     */
    @Test
    fun `a refused command is answered directly, because nothing else will`() {
        assertTrue(wearCommandNeedsDirectAnswer(revisionBefore = 7, revisionAfter = 7))
    }

    /**
     * The workout ended underneath the command. There is no state to publish and
     * the service clears the data item instead, which is how the watch is told.
     */
    @Test
    fun `a workout that ended needs no answer`() {
        assertFalse(wearCommandNeedsDirectAnswer(revisionBefore = 7, revisionAfter = null))
    }

    /**
     * A revision only ever moves forward, but the rule is written as "did it
     * change" rather than "did it increase" — so a future engine that renumbered
     * would still be answered rather than silently ignored.
     */
    @Test
    fun `any change at all counts as applied`() {
        assertFalse(wearCommandNeedsDirectAnswer(revisionBefore = 9, revisionAfter = 2))
    }
}
