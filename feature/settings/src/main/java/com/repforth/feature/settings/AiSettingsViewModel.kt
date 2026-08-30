package com.repforth.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.ai.ProviderConnectionTester
import com.repforth.core.ai.ProviderRepository
import com.repforth.core.ai.ProviderTestResult
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Something to tell the user once, after an action they took. */
sealed interface AiSettingsMessage {
    data object KeySaved : AiSettingsMessage

    data object KeyDeleted : AiSettingsMessage

    data object EverythingDeleted : AiSettingsMessage
}

data class AiSettingsUiState(
    val settings: ProviderSettings = ProviderSettings.Default,
    /** Whether a key is stored for the *selected* provider. */
    val hasKey: Boolean = false,
    /** Which providers have a key, so switching can say what is still missing. */
    val providersWithKeys: Set<ProviderId> = emptySet(),
    /** What is currently typed in the key field. Never read from storage. */
    val keyDraft: String = "",
    val model: String = ProviderSettings.Default.model,
    val baseUrl: String = "",
    val advancedShown: Boolean = false,
    /** True while a connection test is in flight. */
    val testing: Boolean = false,
    /** The last test's outcome, or null if none has been run since a change. */
    val testResult: ProviderTestResult? = null,
    val message: AiSettingsMessage? = null,
) {
    /** The address field is for the generic provider only (§8). */
    val showsBaseUrl: Boolean get() = settings.provider == ProviderId.OPENAI_COMPATIBLE

    val canSaveKey: Boolean get() = keyDraft.isNotBlank()

    /**
     * What has to be filled in before there is anything worth testing.
     *
     * Gemini needs a key, and testing without one would report an
     * authentication failure that is true and useless. The generic provider
     * needs an address instead: a local model server ignores the key field
     * entirely, so requiring one would mean typing a throwaway value to get
     * past a check that protects nothing.
     */
    val canTest: Boolean
        get() = !testing && when (settings.provider) {
            ProviderId.GEMINI -> hasKey
            ProviderId.OPENAI_COMPATIBLE -> baseUrl.isNotBlank()
        }
}

/**
 * The AI provider settings screen (§8).
 *
 * **The key goes one way.** It is typed into [keyDraft], written to the
 * encrypted store, and the draft is cleared — §8's "clear sensitive text-field
 * state after saving". Nothing here ever reads a key back: [AiSettingsUiState]
 * has no field for a stored key, so "never shown again in full" is a property of
 * the type rather than a rule the screen has to remember.
 *
 * **The address is not validated at all**, deliberately — §8 was amended to say
 * so. Whatever is typed is what gets sent. If the server answers, that is the
 * answer; if it does not, the connection test says why in the provider's own
 * terms rather than this app's.
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val providers: ProviderRepository,
    private val tester: ProviderConnectionTester,
) : ViewModel() {

    /**
     * The text fields, while they differ from what is stored.
     *
     * Null means "show what storage says". Without that distinction the stored
     * flow re-emits on every keystroke and puts the cursor back where the saved
     * value ends, which is the classic Compose text-field fight.
     */
    private data class Drafts(
        val key: String = "",
        val model: String? = null,
        val baseUrl: String? = null,
        val advancedShown: Boolean = false,
        val testing: Boolean = false,
        val testResult: ProviderTestResult? = null,
        val message: AiSettingsMessage? = null,
    )

    private val drafts = MutableStateFlow(Drafts())

    val uiState: StateFlow<AiSettingsUiState> = combine(
        providers.settings,
        providers.hasKey,
        providers.providersWithKeys,
        drafts,
    ) { settings, hasKey, withKeys, draft ->
        val baseUrl = draft.baseUrl ?: settings.baseUrl
        AiSettingsUiState(
            settings = settings,
            hasKey = hasKey,
            providersWithKeys = withKeys,
            keyDraft = draft.key,
            model = draft.model ?: settings.model,
            baseUrl = baseUrl,
            advancedShown = draft.advancedShown,
            testing = draft.testing,
            testResult = draft.testResult,
            message = draft.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AiSettingsUiState(),
    )

    /**
     * Switching provider drops the model and address drafts.
     *
     * Each provider has its own stored model id, so keeping a draft across the
     * switch would show one provider's field contents over the other's — and
     * then save it there on the next keystroke.
     */
    fun onProviderChange(provider: ProviderId) {
        drafts.value = drafts.value.copy(
            model = null,
            baseUrl = null,
            key = "",
            // A result from the previous provider says nothing about this one,
            // and leaving it on screen is how a green tick ends up next to a
            // configuration that has never been tested.
            testResult = null,
        )
        viewModelScope.launch { providers.setProvider(provider) }
    }

    fun onKeyChange(key: String) {
        drafts.value = drafts.value.copy(key = key)
    }

    /**
     * Writes the key and forgets what was typed (§8, point 6).
     *
     * Clearing the draft is not cosmetic: it is the difference between a key
     * living in ViewModel state for the rest of the process and living only in
     * the encrypted store.
     */
    fun onSaveKey() {
        val key = drafts.value.key
        if (key.isBlank()) return
        drafts.value = drafts.value.copy(key = "", testResult = null)
        viewModelScope.launch {
            providers.setKey(currentProvider(), key)
            drafts.value = drafts.value.copy(message = AiSettingsMessage.KeySaved)
        }
    }

    fun onDeleteKey() {
        drafts.value = drafts.value.copy(key = "", testResult = null)
        viewModelScope.launch {
            providers.deleteKey(currentProvider())
            drafts.value = drafts.value.copy(message = AiSettingsMessage.KeyDeleted)
        }
    }

    /**
     * The stored provider, read at the moment it is needed.
     *
     * Not `uiState.value.settings.provider`. Switching provider is a write and
     * a flow emission, so the UI state lags it by a frame — and a key typed
     * immediately after the switch would have been written to the provider the
     * user had just moved away from, where they would never find it and where
     * it would sit until a delete-everything. Found by a test that switched and
     * saved in the same breath, which is also what a fast tap does.
     */
    private suspend fun currentProvider(): ProviderId = providers.settings.first().provider

    fun onModelChange(model: String) {
        drafts.value = drafts.value.copy(model = model, testResult = null)
        viewModelScope.launch { providers.setModel(currentProvider(), model) }
    }

    fun onBaseUrlChange(baseUrl: String) {
        drafts.value = drafts.value.copy(baseUrl = baseUrl, testResult = null)
        viewModelScope.launch { providers.setBaseUrl(baseUrl) }
    }

    fun onTimeoutChange(seconds: Int) {
        viewModelScope.launch { providers.setRequestTimeoutSeconds(seconds) }
    }

    /**
     * §8's "Test connection".
     *
     * The result is cleared by any edit that could change it, so what is on
     * screen always describes the configuration currently on screen — a stale
     * "connected" next to a key that has since been replaced is worse than no
     * answer at all.
     */
    fun onTestConnection() {
        if (drafts.value.testing) return
        drafts.value = drafts.value.copy(testing = true, testResult = null)
        viewModelScope.launch {
            val result = tester.test()
            drafts.value = drafts.value.copy(testing = false, testResult = result)
        }
    }

    fun onAdvancedToggled() {
        drafts.value = drafts.value.copy(advancedShown = !drafts.value.advancedShown)
    }

    /** §8's "delete all provider settings", confirmed by the screen first. */
    fun onDeleteEverything() {
        drafts.value = Drafts(message = AiSettingsMessage.EverythingDeleted)
        viewModelScope.launch { providers.deleteAll() }
    }

    fun onMessageShown() {
        drafts.value = drafts.value.copy(message = null)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
