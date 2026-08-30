package com.repforth.feature.settings

import com.repforth.core.ai.FakeAiProvider
import com.repforth.core.ai.ProviderConnectionTester
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.ai.ProviderRepository
import com.repforth.core.ai.ProviderTestResult
import com.repforth.core.datastore.ProviderSettingsDataSource
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
