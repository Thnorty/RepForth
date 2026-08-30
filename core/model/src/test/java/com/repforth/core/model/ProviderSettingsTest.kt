package com.repforth.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line between what is persisted and what is secret (§8, §20).
 *
 * Two of these are guards rather than behaviour tests. They exist because the
 * mistakes they catch — a key field added to the persisted type, a key printed
 * by a generated `toString` — are both one line of ordinary-looking code, and
 * neither produces a symptom until the damage is done.
 */
class ProviderSettingsTest {

    /**
     * [ProviderSettings] goes into plain-text DataStore, so nothing on it may
     * be a credential.
     *
     * Checked by property name because that is what a person adding one would
     * write. It cannot catch a key smuggled into a field called `model`, and it
     * is not meant to: it catches the honest mistake, which is the likely one.
     */
    @Test
    fun `the persisted settings type holds nothing key-shaped`() {
        // Java reflection rather than kotlin-reflect: a data class's backing
        // fields are exactly its constructor properties, and this needs no
        // extra dependency on the test classpath to say so.
        val offenders = ProviderSettings::class.java.declaredFields
            .map { it.name }
            .filter { name -> SECRET_WORDS.any { name.lowercase().contains(it) } }

        assertTrue(
            "ProviderSettings is written to plain-text DataStore, and $offenders " +
                "reads like a credential. Secrets belong in core:secrets, and " +
                "reach a request through ProviderConfig, which is never stored.",
            offenders.isEmpty(),
        )
    }

    /**
     * A data class prints every property it has. If [ProviderConfig] were one,
     * the user's API key would appear in any log line, exception message or
     * debugger frame that touched it — §8 point 5, breached by a default.
     *
     * Watched failing by making ProviderConfig a data class: the key appeared
     * in the string verbatim.
     */
    @Test
    fun `a provider config never prints its key`() {
        val config = ProviderConfig(
            settings = ProviderSettings.Default,
            apiKey = "test-not-a-real-key-9f3a",
        )

        assertFalse(
            "The key must not appear in toString(): ${config}",
            config.toString().contains("test-not-a-real-key-9f3a"),
        )
        assertTrue(
            "It should still say a key is present, so a log is not misleading",
            config.toString().contains("redacted"),
        )
    }

    @Test
    fun `gemini has a fixed endpoint that the stored base url cannot override`() {
        val settings = ProviderSettings.Default.copy(
            provider = ProviderId.GEMINI,
            baseUrl = "https://somewhere.else/v1/",
        )

        assertEquals(ProviderSettings.GEMINI_BASE_URL, settings.effectiveBaseUrl)
    }

    @Test
    fun `the generic provider uses the base url it was given`() {
        val settings = ProviderSettings.Default.copy(
            provider = ProviderId.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com/v1/",
        )

        assertEquals("https://api.example.com/v1/", settings.effectiveBaseUrl)
    }

    @Test
    fun `every provider has a default model`() {
        ProviderId.entries.forEach { provider ->
            assertTrue(
                "$provider has no default model, so a fresh install would send " +
                    "an empty model id",
                ProviderSettings.defaultModelFor(provider).isNotBlank(),
            )
        }
    }

    @Test
    fun `a fresh install has no address of its own`() {
        assertEquals("", ProviderSettings.Default.baseUrl)
    }

    /**
     * Gemini's endpoint is https and fixed, which is now the only thing keeping
     * a Gemini key off a cleartext socket — the app no longer inspects
     * addresses at all (§8, amended). For the generic provider there is nothing
     * equivalent, and that is the accepted consequence.
     */
    @Test
    fun `gemini's own endpoint is https`() {
        assertTrue(
            ProviderSettings.GEMINI_BASE_URL.startsWith("https://"),
        )
    }

    private companion object {
        val SECRET_WORDS = listOf(
            "key", "secret", "token", "password", "credential", "auth", "bearer",
        )
    }
}
