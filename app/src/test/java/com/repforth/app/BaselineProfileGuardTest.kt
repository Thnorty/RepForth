package com.repforth.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The committed baseline profile is real, and describes this app.
 *
 * Generating a profile needs a device, so it is a manual step whose output is
 * checked in — the same arrangement as the screenshot goldens, and with the
 * same hazard: nothing about a normal build notices when it is missing or
 * wrong.
 *
 * This exists because the first generation run **reported BUILD SUCCESSFUL and
 * produced nothing.** The producer module had no `testInstrumentationRunner`,
 * so no test was discovered, so no rules were recorded; the only symptom was a
 * warning in a wall of Gradle output, and the profile directory stayed empty. A
 * silent no-op is the exact failure this repo writes guards against.
 *
 * Watched failing: emptying the file, and deleting every `com/repforth` line
 * from it.
 */
class BaselineProfileGuardTest {

    /** Repo root. Unit tests run with the module directory as the working dir. */
    private val profile = File("src/placeholderRelease/generated/baselineProfiles/baseline-prof.txt")

    @Test
    fun `a baseline profile is committed`() {
        assertTrue(
            "No baseline profile at ${profile.path}. Regenerate it with " +
                "./gradlew :app:generatePlaceholderBaselineProfile — and note that " +
                "doing so on a connected phone would wipe its app data, which is " +
                "why the module is pinned to a managed emulator.",
            profile.isFile,
        )
    }

    /**
     * Enough rules to be a startup profile rather than a stub.
     *
     * The real one is around twelve thousand lines; the floor is deliberately
     * far below that, because the number legitimately moves with every
     * dependency bump and a guard that fails on a Compose upgrade would be
     * turned off within a week.
     */
    @Test
    fun `the profile has enough rules to be worth shipping`() {
        val rules = profile.readLines().count { it.isNotBlank() }
        assertTrue("Only $rules rules in the baseline profile; expected over $MIN_RULES.", rules > MIN_RULES)
    }

    /**
     * The sharp one: it profiled **this** app.
     *
     * A profile recorded against the wrong package, or against a build whose
     * classes were all stripped, is still thousands of lines of framework and
     * Compose rules and looks entirely healthy by size alone. RepForth's own
     * classes appearing is what says the journey actually reached the app.
     */
    @Test
    fun `the profile covers RepForth's own code`() {
        val own = profile.readLines().count { "com/repforth" in it }
        assertTrue(
            "The profile contains $own rules for com/repforth. A profile with none " +
                "of the app's own classes recorded the framework starting up and " +
                "never reached RepForth.",
            own > MIN_OWN_RULES,
        )
    }

    private companion object {
        const val MIN_RULES = 2_000
        const val MIN_OWN_RULES = 100
    }
}
