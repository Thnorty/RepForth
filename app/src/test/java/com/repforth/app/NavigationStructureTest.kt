package com.repforth.app

import com.repforth.app.navigation.Destination
import com.repforth.app.navigation.TopLevelDestination
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * §12: "The workout builder is not a top-level destination; it opens from
     * Plans, from Today, and from the edit action on any saved or generated
     * plan." Promoting it to a tab is the same one-line reversal Coach is
     * guarded against above.
     */
    @Test
    fun `the builder is reachable but is not a tab`() {
        assertFalse(
            "The builder opens from Plans and Today (§12), not the bottom bar.",
            TopLevelDestination.entries.any { it.route is Destination.Builder },
        )
    }

    /**
     * Every non-tab destination has to name its own app-bar title.
     *
     * `titleRes` ends in `else -> settings_title`, which is right for exactly
     * one destination and silently wrong for every one added afterwards. It has
     * already happened once: the builder shipped titled "Settings" because the
     * fallback happened to be correct when it was written.
     *
     * Read from source rather than called, because the function is private to
     * the shell and takes a `NavDestination` that only exists at runtime.
     *
     * Watched failing by removing the AiSettings branch.
     */
    @Test
    fun `every non-tab destination names its own app bar title`() {
        val destinations = File("src/main/java/com/repforth/app/navigation/Destination.kt")
            .readText()
        val shell = File("src/main/java/com/repforth/app/ui/RepForthApp.kt").readText()

        val declared = Regex("""@Serializable data (?:object|class) (\w+)""")
            .findAll(destinations)
            .map { it.groupValues[1] }
            .toList()

        assertTrue("No destinations found; has Destination.kt moved?", declared.isNotEmpty())

        val tabs = TopLevelDestination.entries.map { it.route::class.simpleName }.toSet()

        declared.filterNot { it in tabs }.forEach { name ->
            assertTrue(
                "Destination.$name has no branch in RepForthApp's titleRes, so " +
                    "the app bar will title it \"Settings\". Add one.",
                "Destination.$name::class" in shell,
            )
        }
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
