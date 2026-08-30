package com.repforth.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.ai.ProviderTestResult
import com.repforth.core.model.EndpointRefusal
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings

@Composable
fun AiSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AiSettingsScreen(
        state = state,
        onProviderChange = viewModel::onProviderChange,
        onKeyChange = viewModel::onKeyChange,
        onSaveKey = viewModel::onSaveKey,
        onDeleteKey = viewModel::onDeleteKey,
        onModelChange = viewModel::onModelChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onTimeoutChange = viewModel::onTimeoutChange,
        onAllowCleartextChange = viewModel::onAllowCleartextChange,
        onAdvancedToggled = viewModel::onAdvancedToggled,
        onTestConnection = viewModel::onTestConnection,
        onDeleteEverything = viewModel::onDeleteEverything,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

/**
 * Where the user brings their own AI provider (§8).
 *
 * The disclosure is first, not last. This is the one screen in the app where
 * something the user typed leaves the phone, and the rest of RepForth promises
 * loudly that nothing does — so the exception is stated before the field that
 * enables it, rather than in small print underneath.
 *
 * The key field is write-only. It shows what is being typed and never what is
 * stored: §8 requires a key to be "masked, pasteable, never shown again in
 * full", and the way to keep that promise is to have nothing to show.
 */
@Composable
internal fun AiSettingsScreen(
    state: AiSettingsUiState,
    onProviderChange: (ProviderId) -> Unit,
    onKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onDeleteKey: () -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onAllowCleartextChange: (Boolean) -> Unit,
    onAdvancedToggled: () -> Unit,
    onTestConnection: () -> Unit,
    onDeleteEverything: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        item(key = "disclosure") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.ai_disclosure),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Space.s4),
                )
            }
        }

        item(key = "provider") {
            ChoiceRow(
                label = stringResource(R.string.ai_provider),
                options = ProviderId.entries,
                selected = state.settings.provider,
                labelOf = {
                    stringResource(
                        when (it) {
                            ProviderId.GEMINI -> R.string.ai_provider_gemini
                            ProviderId.OPENAI_COMPATIBLE -> R.string.ai_provider_openai
                        },
                    )
                },
                onSelected = onProviderChange,
            )
        }

        item(key = "key") {
            KeyField(
                key = state.keyDraft,
                hasStoredKey = state.hasKey,
                canSave = state.canSaveKey,
                onKeyChange = onKeyChange,
                onSave = onSaveKey,
                onDelete = onDeleteKey,
            )
        }

        item(key = "model") {
            OutlinedTextField(
                value = state.model,
                onValueChange = onModelChange,
                label = { Text(stringResource(R.string.ai_model)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.ai_model_hint,
                            ProviderSettings.defaultModelFor(state.settings.provider),
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.showsBaseUrl) {
            item(key = "base-url") {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text(stringResource(R.string.ai_base_url)) },
                    placeholder = { Text(stringResource(R.string.ai_base_url_hint)) },
                    isError = state.baseUrlRefusal != null,
                    supportingText = state.baseUrlRefusal?.let { refusal ->
                        { Text(stringResource(refusal.messageRes())) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "test") {
            TestConnection(
                enabled = state.canTest,
                testing = state.testing,
                result = state.testResult,
                onTest = onTestConnection,
            )
        }

        item(key = "advanced") {
            ActionRow(
                label = stringResource(R.string.ai_advanced),
                detail = stringResource(R.string.ai_timeout),
                enabled = true,
                onClick = onAdvancedToggled,
            )
        }

        if (state.advancedShown) {
            item(key = "timeout") {
                TimeoutSlider(
                    seconds = state.settings.requestTimeoutSeconds,
                    onChange = onTimeoutChange,
                )
            }

            item(key = "cleartext") {
                SwitchRow(
                    label = stringResource(R.string.ai_cleartext),
                    detail = stringResource(R.string.ai_cleartext_sub),
                    checked = state.settings.allowCleartext,
                    onCheckedChange = onAllowCleartextChange,
                )
            }
        }

        item(key = "delete-all") {
            ActionRow(
                label = stringResource(R.string.ai_delete_all),
                detail = stringResource(R.string.ai_delete_all_sub),
                enabled = true,
                destructive = true,
                onClick = { confirmingDelete = true },
            )
        }
    }

    if (confirmingDelete) {
        DestructiveDialog(
            title = stringResource(R.string.ai_delete_all_title),
            body = stringResource(R.string.ai_delete_all_body),
            confirm = stringResource(R.string.ai_delete_all_confirm),
            onConfirm = {
                confirmingDelete = false
                onDeleteEverything()
            },
            onDismiss = { confirmingDelete = false },
            // An export has never contained a key and never will, so telling
            // the user to take one first would be advice that does not work.
            offerExport = false,
        )
    }

    state.message?.let { message ->
        val text = when (message) {
            AiSettingsMessage.KeySaved -> stringResource(R.string.ai_key_saved)
            AiSettingsMessage.KeyDeleted -> stringResource(R.string.ai_key_deleted)
            AiSettingsMessage.EverythingDeleted -> stringResource(R.string.ai_deleted_all)
        }
        AlertDialog(
            onDismissRequest = onMessageShown,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = onMessageShown) { Text("OK") } },
        )
    }
}

/**
 * The API key: typed, masked, saved, forgotten.
 *
 * The field is emptied by the ViewModel the moment the key is written, so the
 * plaintext lives in this composition for as long as it takes to press Save and
 * no longer. The status line below says whether a key is stored, which is the
 * only thing this screen is ever allowed to know about one.
 */
@Composable
private fun KeyField(
    key: String,
    hasStoredKey: Boolean,
    canSave: Boolean,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text(stringResource(R.string.ai_key)) },
            placeholder = { Text(stringResource(R.string.ai_key_hint)) },
            singleLine = true,
            // Masked, but still pasteable — §8 asks for both, and a field that
            // refuses a paste is a field people retype from a screenshot.
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                // No autocorrect and no suggestions: an API key in the
                // keyboard's learned-words dictionary is a copy of the key
                // outside this app's storage entirely.
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                // Tells accessibility services this is a password, so it is not
                // read aloud character by character to a room.
                .semantics { password() },
        )

        Text(
            text = stringResource(
                if (hasStoredKey) R.string.ai_key_stored else R.string.ai_key_missing,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Button(onClick = onSave, enabled = canSave) {
                Text(stringResource(R.string.ai_key_save))
            }
            if (hasStoredKey) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.ai_key_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * §8's "Test connection", and what it found.
 *
 * The answer is a sentence, not a tick. Every failure this can report has a
 * different thing for the user to do about it — replace the key, fix the model
 * name, wait, check the address, or nothing at all because the problem is
 * their provider account — and a coloured icon says none of that.
 */
@Composable
private fun TestConnection(
    enabled: Boolean,
    testing: Boolean,
    result: ProviderTestResult?,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Button(onClick = onTest, enabled = enabled) {
            Text(
                stringResource(if (testing) R.string.ai_test_running else R.string.ai_test),
            )
        }

        result?.let {
            val failed = it is ProviderTestResult.Failed
            Text(
                text = stringResource(it.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TimeoutSlider(seconds: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Text(
            text = pluralStringResource(R.plurals.ai_timeout_seconds, seconds, seconds),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = ProviderSettings.MIN_TIMEOUT_SECONDS.toFloat()..
                ProviderSettings.MAX_TIMEOUT_SECONDS.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Target.min),
        )
    }
}

/** What the connection test found, in a sentence that says what to do next. */
private fun ProviderTestResult.messageRes(): Int = when (this) {
    is ProviderTestResult.Ok ->
        if (modelConfirmed) R.string.ai_test_ok else R.string.ai_test_ok_unconfirmed

    is ProviderTestResult.Failed -> when (failure) {
        ProviderFailure.AUTHENTICATION -> R.string.ai_test_auth
        ProviderFailure.MODEL_NOT_FOUND -> R.string.ai_test_model
        ProviderFailure.QUOTA -> R.string.ai_test_quota
        ProviderFailure.NETWORK -> R.string.ai_test_network
        ProviderFailure.FORMAT -> R.string.ai_test_format
        ProviderFailure.ENDPOINT_REFUSED -> R.string.ai_test_endpoint
        ProviderFailure.SERVER -> R.string.ai_test_server
    }
}

/**
 * Why an address was refused, in words that say what to do about it.
 *
 * §8 asks for actionable errors. "Invalid URL" is not one; "plain http:// works
 * only for a server on your own network" tells the user both what happened and
 * which of their two options applies.
 */
private fun EndpointRefusal.messageRes(): Int = when (this) {
    EndpointRefusal.BLANK, EndpointRefusal.MALFORMED -> R.string.ai_url_malformed
    EndpointRefusal.UNSUPPORTED_SCHEME -> R.string.ai_url_scheme
    EndpointRefusal.CLEARTEXT_NOT_ALLOWED -> R.string.ai_url_cleartext
    EndpointRefusal.CLEARTEXT_NOT_LOOPBACK -> R.string.ai_url_not_local
    EndpointRefusal.EMBEDDED_CREDENTIALS -> R.string.ai_url_credentials
}
