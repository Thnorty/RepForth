package com.repforth.app

import com.repforth.app.navigation.Destination
import com.repforth.app.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * §12 fixes the bottom bar at four destinations, and that decision carries an
 * argument behind it: Coach is a mode inside the builder, not a fifth tab,
 * because AI is optional and a tab would degrade into a setup prompt for users
 * who never configure a provider.
 *
 * A fifth tab is a one-line change, which is exactly the problem — it looks like
 * a UI tweak and is actually a reversal of a product decision. This test makes
 * that reversal fail the build, so it has to be argued rather than slipped in.
 */
class NavigationStructureTest {

    @Test
    fun `the bottom bar has exactly the four destinations section 12 fixes`() {
        assertEquals(
            "The top-level destinations changed. §12 fixes these four; if this is " +
                "a real product decision, update the guideline in the same change.",
            listOf(
                Destination.Today,
                Destination.Plans,
                Destination.Exercises,
                Destination.Progress,
            ),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun `settings is reachable but is not a tab`() {
        assertFalse(
            "Settings opens from the top bar (§12), not the bottom bar.",
            TopLevelDestination.entries.any { it.route == Destination.Settings },
        )
    }

    @Test
    fun `every top level destination has a distinct label`() {
        val labels = TopLevelDestination.entries.map { it.labelRes }
        assertEquals(
            "Two tabs share a label resource, so one of them is mislabelled.",
            labels.size,
            labels.toSet().size,
        )
    }
}
