package com.repforth.core.secrets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The secret store, against the real Android Keystore.
 *
 * These are instrumentation tests because there is nothing to test otherwise:
 * the whole point of the class is that the master key lives in hardware-backed
 * platform storage, and a JVM test would either mock that away — leaving the
 * assertion "Tink encrypts things", which is Google's test, not ours — or fail
 * to run at all.
 *
 * What is worth asserting is the part this project wrote: that a secret comes
 * back, that what lands on disk is not the secret, that ciphertext is bound to
 * the slot it was written for, and that deleting means deleted.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = KeystoreSecretStore(context, Dispatchers.IO)

    @Before
    fun setUp() = runTest {
        store.clear()
    }

    @Test
    fun aStoredSecretComesBack() = runTest {
        store.put(GEMINI, SECRET)

        assertEquals(SECRET, store.get(GEMINI))
    }

    @Test
    fun anAbsentSecretIsNull() = runTest {
        assertNull(store.get(GEMINI))
        assertFalse(store.contains(GEMINI))
    }

    @Test
    fun writingTwiceReplaces() = runTest {
        store.put(GEMINI, SECRET)
        store.put(GEMINI, "second-value")

        assertEquals("second-value", store.get(GEMINI))
    }

    /**
     * The assertion the whole class exists for. If the plaintext key is
     * recoverable from the file, nothing else here matters.
     */
    @Test
    fun theSecretIsNotOnDiskInPlaintext() = runTest {
        store.put(GEMINI, SECRET)

        val onDisk = File(context.filesDir, "secrets").walkTopDown()
            .filter { it.isFile }
            .toList()

        assertTrue("Something should have been written", onDisk.isNotEmpty())
        onDisk.forEach { file ->
            val bytes = file.readBytes()
            assertFalse(
                "${file.name} contains the raw secret",
                String(bytes, Charsets.ISO_8859_1).contains(SECRET),
            )
        }
    }

    /**
     * Ciphertext is bound to its slot by the AEAD's associated data, so a file
     * moved from one provider's slot to another's fails to decrypt rather than
     * quietly answering as the wrong key.
     */
    @Test
    fun ciphertextDoesNotDecryptUnderAnotherId() = runTest {
        store.put(GEMINI, SECRET)

        val source = File(context.filesDir, "secrets").listFiles()!!.single()
        val target = File(source.parentFile, hexOf(OPENAI))
        source.copyTo(target, overwrite = true)

        assertNull("Moved ciphertext must not resolve as another provider", store.get(OPENAI))
    }

    @Test
    fun deleteRemovesOnlyThatSecret() = runTest {
        store.put(GEMINI, SECRET)
        store.put(OPENAI, "other-value")

        store.delete(GEMINI)

        assertNull(store.get(GEMINI))
        assertEquals("other-value", store.get(OPENAI))
    }

    @Test
    fun clearRemovesEverything() = runTest {
        store.put(GEMINI, SECRET)
        store.put(OPENAI, "other-value")

        store.clear()

        assertNull(store.get(GEMINI))
        assertNull(store.get(OPENAI))
    }

    /**
     * Written after the store is torn down and rebuilt, because a keyset held
     * only in memory would pass every test above and lose every secret the next
     * time the app started.
     */
    @Test
    fun secretsSurviveANewStoreInstance() = runTest {
        store.put(GEMINI, SECRET)

        val reopened = KeystoreSecretStore(context, Dispatchers.IO)

        assertEquals(SECRET, reopened.get(GEMINI))
    }

    private fun hexOf(id: SecretId) =
        id.value.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    private companion object {
        val GEMINI = SecretId("provider.gemini.key")
        val OPENAI = SecretId("provider.openai.key")

        /** Shaped like a real key, and deliberately not one. */
        const val SECRET = "test-not-a-real-key-0123456789"
    }
}
