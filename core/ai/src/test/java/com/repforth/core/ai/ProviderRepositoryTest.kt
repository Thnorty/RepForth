package com.repforth.core.ai

import com.repforth.core.datastore.ProviderSettingsDataSource
import com.repforth.core.model.ProviderId
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.InMemorySecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The join between a provider's settings and its key (§8).
 *
 * The interesting cases are all about the two halves getting out of step: a key
 * stored for one provider while another is selected, a key deleted while
 * settings remain, and settings deleted while a key remains — the last of which
 * is the one that leaves something billable on a phone the user believes they
 * have wiped.
 */
class ProviderRepositoryTest {

    private val secrets = InMemorySecretStore()
    private val settings = ProviderSettingsDataSource(FakePreferencesStore())
    private val repository = ProviderRepository(settings, secrets)

    @Test
    fun `no key is stored to begin with`() = runTest {
        assertFalse(repository.hasKey.first())
        assertNull(repository.configFor(repository.settings.first()))
    }

    /**
     * The generic provider works without a key, because a local model server
     * ignores it. Gemini does not.
     */
    @Test
    fun `only a provider that needs a key is blocked without one`() = runTest {
        assertNull(
            "Gemini cannot be called without a key",
            repository.configFor(repository.settings.first()),
        )

        repository.setProvider(ProviderId.OPENAI_COMPATIBLE)
        val config = repository.configFor(repository.settings.first())

        assertNotNull("A local model server needs no key", config)
        assertEquals("", config!!.apiKey)
    }

    @Test
    fun `a stored key makes a config available`() = runTest {
        repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        assertTrue(repository.hasKey.first())
        val config = repository.configFor(repository.settings.first())
        assertNotNull(config)
        assertEquals("test-not-a-real-key", config!!.apiKey)
        assertEquals(ProviderId.GEMINI, config.provider)
    }

    /**
     * Each provider gets its own slot, so configuring the second one does not
     * silently overwrite the first one's key — which the user would discover
     * only by switching back and finding themselves rejected.
     */
    @Test
    fun `keys are kept per provider`() = runTest {
        repository.setKey(ProviderId.GEMINI, "gemini-test-key")
        repository.setKey(ProviderId.OPENAI_COMPATIBLE, "openai-test-key")

        repository.setProvider(ProviderId.OPENAI_COMPATIBLE)
        assertEquals(
            "openai-test-key",
            repository.configFor(repository.settings.first())!!.apiKey,
        )

        repository.setProvider(ProviderId.GEMINI)
        assertEquals(
            "gemini-test-key",
            repository.configFor(repository.settings.first())!!.apiKey,
        )
    }

    @Test
    fun `hasKey follows the selected provider`() = runTest {
        repository.setKey(ProviderId.GEMINI, "gemini-test-key")

        repository.setProvider(ProviderId.OPENAI_COMPATIBLE)

        assertFalse(
            "The other provider has no key, and the screen has to say so",
            repository.hasKey.first(),
        )
    }

    /**
     * A blank key deletes rather than storing "".
     *
     * A stored empty string would report `hasKey = true` and then fail every
     * request with an authentication error, which reads to the user as "my key
     * is wrong" rather than "there is no key".
     */
    @Test
    fun `a blank key is a deletion`() = runTest {
        repository.setKey(ProviderId.GEMINI, "test-not-a-real-key")

        repository.setKey(ProviderId.GEMINI, "   ")

        assertFalse(repository.hasKey.first())
        assertTrue(secrets.storedIds.isEmpty())
    }

    @Test
    fun `a key is trimmed, because pasting one brings whitespace`() = runTest {
        repository.setKey(ProviderId.GEMINI, "  test-not-a-real-key\n")

        assertEquals(
            "test-not-a-real-key",
            repository.configFor(repository.settings.first())!!.apiKey,
        )
    }

    @Test
    fun `deleting a key leaves the settings alone`() = runTest {
        repository.setProvider(ProviderId.OPENAI_COMPATIBLE)
        repository.setBaseUrl("https://api.example.com/v1/")
        repository.setKey(ProviderId.OPENAI_COMPATIBLE, "test-not-a-real-key")

        repository.deleteKey(ProviderId.OPENAI_COMPATIBLE)

        assertFalse(repository.hasKey.first())
        assertEquals(
            "Deleting a key is not deleting the endpoint",
            "https://api.example.com/v1/",
            repository.settings.first().baseUrl,
        )
    }

    /**
     * §8's "delete all provider settings" has to reach the ciphertext too.
     *
     * Asserted on the store rather than through [ProviderRepository.hasKey]:
     * "the app reports no key" and "there is no key on the device" are
     * different claims, and only the second is what the user asked for.
     */
    @Test
    fun `deleting everything removes both halves for every provider`() = runTest {
        ProviderId.entries.forEach { repository.setKey(it, "test-key-for-testing") }
        repository.setBaseUrl("https://api.example.com/v1/")
        repository.setAllowCleartext(true)

        repository.deleteAll()

        assertTrue("Ciphertext left behind: ${secrets.storedIds}", secrets.storedIds.isEmpty())
        assertEquals("", repository.settings.first().baseUrl)
        assertFalse(repository.settings.first().allowCleartext)
    }

    @Test
    fun `the providers that have keys are reported, so the screen can say which`() = runTest {
        repository.setKey(ProviderId.OPENAI_COMPATIBLE, "test-not-a-real-key")

        assertEquals(setOf(ProviderId.OPENAI_COMPATIBLE), repository.providersWithKeys.first())
    }
}
