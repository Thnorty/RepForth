package com.repforth.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §13 says English and Turkish ship in lockstep and neither is a fallback for
 * the other. That is enforced by the type, so these tests are really checking
 * that the enforcement cannot be bypassed.
 */
class LocalizedInstructionsTest {

    private fun text(s: String) = InstructionText(steps = listOf(s, "$s step"))

    @Test
    fun `both languages present is accepted`() {
        val instructions = LocalizedInstructions(
            mapOf(Language.ENGLISH to text("Push"), Language.TURKISH to text("İt")),
        )
        assertEquals("Push", instructions[Language.ENGLISH].steps.first())
        assertEquals("İt", instructions[Language.TURKISH].steps.first())
    }

    @Test
    fun `a missing language is rejected at construction, not at read time`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LocalizedInstructions(mapOf(Language.ENGLISH to text("Push")))
        }
        assertTrue(error.message.orEmpty().contains("tr"))
    }

    @Test
    fun `language tags round-trip and unknown tags are rejected`() {
        Language.entries.forEach { assertEquals(it, Language.fromTag(it.tag)) }
        assertEquals(null, Language.fromTag("de"))
    }

    @Test
    fun `instruction text joins its steps the way upstream does`() {
        // Upstream ships the joined paragraph too; it must round-trip exactly,
        // because that equivalence is why the paragraph is not stored.
        assertEquals("Sit down. Push up.", InstructionText(listOf("Sit down.", "Push up.")).text)
    }

    @Test
    fun `an exercise with no steps is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { InstructionText(emptyList()) }
    }

    @Test
    fun `a blank exercise id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ExerciseId("  ") }
    }
}
