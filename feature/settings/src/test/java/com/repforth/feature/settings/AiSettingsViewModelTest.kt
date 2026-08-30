package com.repforth.feature.settings

import com.repforth.core.ai.FakeAiProvider
import com.repforth.core.ai.ProviderConnectionTester
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.ai.ProviderRepository
import com.repforth.core.ai.ProviderTestResult
import com.repforth.core.datastore.ProviderSettingsDataSource
import com.repforth.core.model.EndpointRefusal
import com.repforth.core.model.ProviderId
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.InMemorySecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The AI settings screen's logic (§8).
 *
 * Two of these are about the key, and both are about it going one way only:
 * that saving clears the field it was typed into, and that nothing on this
 * screen can ever read a stored key back. The rest are about the address field,
 * where a wrong answer sends a credential somewhere it should not go.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val secrets = InMemorySecretStore()
    private val gemini = FakeAiProvider(ProviderId.GEMINI)
    private val openAi = FakeAiProvider(ProviderId.OPENAI_COMPATIBLE)
    private lateinit var repository: ProviderRepository
    private lateinit var viewModel: AiSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = ProviderRepository(
            ProviderSettingsDataSource(FakePreferencesStore()),
            secrets,
        )
        viewModel = AiSettingsViewModel(
            repository,
            ProviderConnectionTester(
                repository,
                mapOf(ProviderId.GEMINI to gemini, ProviderId.OPENAI_COMPATIBLE to openAi),
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Starts collecting, and returns the latest state.
     *
     * `uiState` is a `WhileSubscribed` flow: with nothing collecting it, its
     * value never leaves the initial one, and every assertion here would read a
     * state the ViewModel never produced. The collector lives in
     * `backgroundScope` so `runTest` cancels it rather than waiting on it.
     */
    private fun TestScope.activate() {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
    }

    /**
     * §8, point 6: "clear sensitive text-field state after saving."
     *
     * Not cosmetic. The draft lives in a StateFlow for the life of the
     * ViewModel, so leaving it there means the plaintext key stays in memory,
     * survives a rotation, and is restored into a visible field — long after the
     * user believes they have handed it over.
     *
     * Watched failing with the `copy(key = "")` removed from onSaveKey.
     */
    @Test
    fun `saving a key clears the field it was typed into`() = runTest(dispatcher) {
        activate()
        viewModel.onKeyChange("test-not-a-real-key")
        viewModel.onSaveKey()
        testScheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.keyDraft)
        assertEquals(
            "The key should have reached the store",
            "test-not-a-real-key",
            secrets.get(com.repforth.core.secrets.SecretId("provider.gemini.key")),
        )
    }

    /**
     * The screen has no field for a stored key, so "never shown again in full"
     * is a property of the type rather than a rule someone has to remember.
     *
     * Asserted over the whole state object: if a `storedKey` is ever added,
     * this fails without anyone having to think of updating it.
     */
    @Test
    fun `no stored key is ever readable through the ui state`() = runTest(dispatcher) {
        activate()
        viewModel.onKeyChange("test-not-a-real-key")
        viewModel.onSaveKey()
        testScheduler.advanceUntilIdle()

        val rendered = viewModel.uiState.value.toString()
        assertFalse(
            "A stored key must not be reachable from the screen's state: $rendered",
            rendered.contains("test-not-a-real-key"),
        )
        assertTrue(
            "It should still know that a key exists",
            viewModel.uiState.value.hasKey,
        )
    }

    @Test
    fun `deleting the key leaves nothing on the device`() = runTest(dispatcher) {
        activate()
        viewModel.onKeyChange("test-not-a-real-key")
        viewModel.onSaveKey()
        testScheduler.advanceUntilIdle()

        viewModel.onDeleteKey()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasKey)
        assertTrue(secrets.storedIds.isEmpty())
    }

    /**
     * Switching provider drops the drafts, or one provider's typed model id is
     * shown over the other's — and written there on the next keystroke.
     */
    @Test
    fun `switching provider stops showing the other one's draft`() = runTest(dispatcher) {
        activate()
        viewModel.onModelChange("something-i-typed")
        testScheduler.advanceUntilIdle()

        viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
        testScheduler.advanceUntilIdle()

        assertEquals(
            "The generic provider should show its own default, not Gemini's draft",
            com.repforth.core.model.ProviderSettings.DEFAULT_OPENAI_MODEL,
            viewModel.uiState.value.model,
        )
    }

    @Test
    fun `a typed key is dropped when the provider changes`() = runTest(dispatcher) {
        activate()
        viewModel.onKeyChange("half-typed-key")

        viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
        testScheduler.advanceUntilIdle()

        assertEquals(
            "A key typed for one provider must not be saved against another",
            "",
            viewModel.uiState.value.keyDraft,
        )
    }

    @Test
    fun `the address field is shown for the generic provider only`() = runTest(dispatcher) {
        activate()
        assertFalse(
            "Gemini's endpoint is fixed, so there is nothing to type",
            viewModel.uiState.value.showsBaseUrl,
        )

        viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showsBaseUrl)
    }

    /** An empty field is not an error; it is a field nobody has filled in yet. */
    @Test
    fun `an empty address shows no error`() = runTest(dispatcher) {
        activate()
        viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.baseUrlRefusal)
    }

    @Test
    fun `a cleartext address is refused while the developer setting is off`() =
        runTest(dispatcher) {
            activate()
            viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
            viewModel.onBaseUrlChange("http://localhost:11434/v1/")
            testScheduler.advanceUntilIdle()

            assertEquals(
                EndpointRefusal.CLEARTEXT_NOT_ALLOWED,
                viewModel.uiState.value.baseUrlRefusal,
            )
        }

    /**
     * The developer setting widens cleartext to local addresses. It does not
     * turn the rule off, and the screen has to say so while the user is typing
     * rather than at the moment the request goes out.
     */
    @Test
    fun `the developer setting allows a local address but not a public one`() =
        runTest(dispatcher) {
            activate()
            viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
            viewModel.onAllowCleartextChange(true)
            viewModel.onBaseUrlChange("http://localhost:11434/v1/")
            testScheduler.advanceUntilIdle()

            assertNull(
                "A loopback model server is what the setting is for",
                viewModel.uiState.value.baseUrlRefusal,
            )

            viewModel.onBaseUrlChange("http://api.example.com/v1/")
            testScheduler.advanceUntilIdle()

            assertEquals(
                EndpointRefusal.CLEARTEXT_NOT_LOOPBACK,
                viewModel.uiState.value.baseUrlRefusal,
            )
        }

    /**
     * Testing with no key would report an authentication failure, which is true
     * and useless — the user has not finished setting up, and the button should
     * say so by being off rather than by failing.
     */
    /**
     * Switching provider and saving a key without waiting in between.
     *
     * This found a real bug. `onSaveKey` read the provider from `uiState`,
     * which lags the write by an emission, so the key went to the provider the
     * user had just moved *away* from — where they would never see it, where
     * the new provider would keep reporting "no key", and where it would sit
     * until a delete-everything. A fast tap does exactly this.
     *
     * Watched failing against the previous version: the key landed under
     * `provider.gemini.key`.
     */
    @Test
    fun `a key saved straight after switching provider goes to the new one`() =
        runTest(dispatcher) {
            activate()
            viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
            viewModel.onKeyChange("test-not-a-real-key")
            viewModel.onSaveKey()
            testScheduler.advanceUntilIdle()

            assertEquals(
                "The key belongs to the provider that is now selected",
                setOf("provider.openai_compatible.key"),
                secrets.storedIds,
            )
        }

    @Test
    fun `the test button is off until a key is stored`() = runTest(dispatcher) {
        activate()
        assertFalse(viewModel.uiState.value.canTest)

        viewModel.onKeyChange("test-not-a-real-key")
        viewModel.onSaveKey()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canTest)
    }

    @Test
    fun `a connection test reaches the adapter for the selected provider`() =
        runTest(dispatcher) {
            activate()
            viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
            viewModel.onKeyChange("test-not-a-real-key")
            viewModel.onSaveKey()
            testScheduler.advanceUntilIdle()

            viewModel.onTestConnection()
            testScheduler.advanceUntilIdle()

            assertEquals(
                "The Gemini adapter must not be asked about an OpenAI configuration",
                0,
                gemini.calls.size,
            )
            assertEquals(1, openAi.calls.size)
            assertEquals("test-not-a-real-key", openAi.calls.single().apiKey)
        }

    @Test
    fun `a failure is reported rather than thrown`() = runTest(dispatcher) {
        activate()
        gemini.next = ProviderTestResult.Failed(ProviderFailure.AUTHENTICATION)
        viewModel.onKeyChange("test-not-a-real-key")
        viewModel.onSaveKey()
        testScheduler.advanceUntilIdle()

        viewModel.onTestConnection()
        testScheduler.advanceUntilIdle()

        assertEquals(
            ProviderTestResult.Failed(ProviderFailure.AUTHENTICATION),
            viewModel.uiState.value.testResult,
        )
        assertFalse(viewModel.uiState.value.testing)
    }

    /**
     * A green "connected" left standing next to a key that has since been
     * replaced is worse than no answer: it reports a configuration that was
     * never tested.
     *
     * Watched failing with the `testResult = null` removed from `onSaveKey`.
     */
    @Test
    fun `an edit clears the previous result`() = runTest(dispatcher) {
        activate()
        viewModel.onKeyChange("test-not-a-real-key")
        viewModel.onSaveKey()
        testScheduler.advanceUntilIdle()
        viewModel.onTestConnection()
        testScheduler.advanceUntilIdle()
        assertEquals(ProviderTestResult.Ok(modelConfirmed = true), viewModel.uiState.value.testResult)

        viewModel.onModelChange("a-different-model")
        testScheduler.advanceUntilIdle()

        assertNull(
            "The result described the old model, not this one",
            viewModel.uiState.value.testResult,
        )
    }

    @Test
    fun `deleting everything clears both the settings and the keys`() = runTest(dispatcher) {
        activate()
        viewModel.onProviderChange(ProviderId.OPENAI_COMPATIBLE)
        viewModel.onBaseUrlChange("https://api.example.com/v1/")
        viewModel.onKeyChange("test-not-a-real-key")
        viewModel.onSaveKey()
        testScheduler.advanceUntilIdle()

        viewModel.onDeleteEverything()
        testScheduler.advanceUntilIdle()

        assertTrue("Ciphertext left behind: ${secrets.storedIds}", secrets.storedIds.isEmpty())
        assertEquals("", repository.settings.first().baseUrl)
        assertEquals(ProviderId.GEMINI, viewModel.uiState.value.settings.provider)
    }
}
