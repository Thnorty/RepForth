package com.repforth.core.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `tools/gemini-schema.json` must be the schema this code actually sends.
 *
 * That file is what `tools/probe-gemini-schema.ps1` puts in front of the live
 * Gemini endpoint, and it is the only way this project can find out that a
 * schema is rejected — MockWebServer accepts anything, so every other test here
 * passes against a schema Google refuses. A stale dump would mean the probe
 * cheerfully verifying a schema nobody ships, which is worse than no probe: it
 * would report PASS while the app kept failing.
 *
 * Regenerate with:
 *
 *   ./gradlew :core:ai:testDebugUnitTest --tests '*SchemaDumpGuardTest*' -Drepforth.regenerate=true
 *
 * Then re-run the probe before trusting the result.
 */
class SchemaDumpGuardTest {

    @Test
    fun `the dumped schema matches the schema the app sends`() {
        val dump = File("../../tools/gemini-schema.json")
        val live = AiWorkoutJsonSchema.value.toString()

        if (System.getProperty("repforth.regenerate") == "true") {
            dump.parentFile?.mkdirs()
            dump.writeText(live)
            return
        }

        assertTrue(
            "Missing ${dump.absolutePath}. Regenerate it — see this test's comment.",
            dump.exists(),
        )
        assertEquals(
            "tools/gemini-schema.json is stale, so the Gemini probes are testing " +
                "a schema this app no longer sends. Regenerate it — see this " +
                "test's comment — and re-run tools/probe-gemini-fields.ps1.",
            live,
            dump.readText().trim(),
        )
    }
}
