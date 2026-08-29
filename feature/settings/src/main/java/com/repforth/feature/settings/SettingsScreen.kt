package com.repforth.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.model.Language
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.UnitSystem
import com.repforth.core.transfer.ImportFailure

/**
 * Settings (§12: opened from the top bar, not the bottom one).
 *
 * Two kinds of row, deliberately different to touch. Preferences apply as you
 * change them, because they are reversible. The data actions open the system
 * file picker or ask a question first, because they are not.
 */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The system picker, so the file goes wherever the user keeps things and
    // this app never asks for storage permission.
    val exportTo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri -> uri?.let(viewModel::onExportTo) }

    val importFrom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onImportFrom) }

    val exportName = stringResource(R.string.settings_export_name)

    SettingsScreen(
        state = state,
        onThemeChange = viewModel::onThemeChange,
        onLanguageChange = viewModel::onLanguageChange,
        onUnitsChange = viewModel::onUnitsChange,
        onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
        onHapticsChange = viewModel::onHapticsChange,
        onReducedMotionChange = viewModel::onReducedMotionChange,
        onExport = { exportTo.launch(exportName) },
        // Not filtered to JSON: a file manager that saved the export without a
        // recognised type would then be unable to offer it back, and a file
        // that cannot be re-opened is not much of an export.
        onImport = { importFrom.launch(arrayOf("*/*")) },
        onImportConfirmed = viewModel::onImportConfirmed,
        onImportCancelled = viewModel::onImportCancelled,
        onDeleteWorkoutData = viewModel::onDeleteWorkoutData,
        onResetApp = viewModel::onResetApp,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (Language?) -> Unit,
    onUnitsChange: (UnitSystem) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportConfirmed: () -> Unit,
    onImportCancelled: () -> Unit,
    onDeleteWorkoutData: () -> Unit,
    onResetApp: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        item(key = "appearance") { SectionLabel(stringResource(R.string.settings_appearance)) }

        item(key = "theme") {
            ChoiceRow(
                label = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = state.preferences.themeMode,
                labelOf = {
                    stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        },
                    )
                },
                onSelected = onThemeChange,
            )
        }

        item(key = "language") {
            // Null is a real choice here, not an absence: it means follow the
            // system locale, which is the default.
            val options = listOf(null, Language.ENGLISH, Language.TURKISH)
            ChoiceRow(
                label = stringResource(R.string.settings_language),
                options = options,
                selected = state.preferences.language,
                labelOf = {
                    stringResource(
                        when (it) {
                            null -> R.string.settings_language_system
                            Language.ENGLISH -> R.string.settings_language_en
                            Language.TURKISH -> R.string.settings_language_tr
                        },
                    )
                },
                onSelected = onLanguageChange,
            )
        }

        item(key = "units") {
            ChoiceRow(
                label = stringResource(R.string.settings_units),
                options = UnitSystem.entries,
                selected = state.preferences.unitSystem,
                labelOf = {
                    stringResource(
                        when (it) {
                            UnitSystem.METRIC -> R.string.settings_units_metric
                            UnitSystem.IMPERIAL -> R.string.settings_units_imperial
                        },
                    )
                },
                onSelected = onUnitsChange,
            )
        }

        item(key = "workout") { SectionLabel(stringResource(R.string.settings_workout)) }

        item(key = "keep-awake") {
            SwitchRow(
                label = stringResource(R.string.settings_keep_awake),
                detail = stringResource(R.string.settings_keep_awake_sub),
                checked = state.preferences.keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
            )
        }

        item(key = "haptics") {
            SwitchRow(
                label = stringResource(R.string.settings_haptics),
                detail = stringResource(R.string.settings_haptics_sub),
                checked = state.preferences.hapticsEnabled,
                onCheckedChange = onHapticsChange,
            )
        }

        item(key = "motion") {
            SwitchRow(
                label = stringResource(R.string.settings_reduced_motion),
                detail = stringResource(R.string.settings_reduced_motion_sub),
                checked = state.preferences.reducedMotion,
                onCheckedChange = onReducedMotionChange,
            )
        }

        item(key = "data") { SectionLabel(stringResource(R.string.settings_data)) }

        item(key = "export") {
            ActionRow(
                label = stringResource(R.string.settings_export),
                detail = stringResource(R.string.settings_export_sub),
                enabled = !state.busy,
                onClick = onExport,
            )
        }

        item(key = "import") {
            ActionRow(
                label = stringResource(R.string.settings_import),
                detail = stringResource(R.string.settings_import_sub),
                enabled = !state.busy,
                onClick = onImport,
            )
        }

        item(key = "delete") {
            ActionRow(
                label = stringResource(R.string.settings_delete_workouts),
                detail = stringResource(R.string.settings_delete_workouts_sub),
                enabled = !state.busy,
                destructive = true,
                onClick = { confirmingDelete = true },
            )
        }

        item(key = "reset") {
            ActionRow(
                label = stringResource(R.string.settings_reset),
                detail = stringResource(R.string.settings_reset_sub),
                enabled = !state.busy,
                destructive = true,
                onClick = { confirmingReset = true },
            )
        }

        item(key = "privacy") {
            Text(
                text = stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s6, bottom = Space.s4),
            )
        }
    }

    state.pendingImport?.let { pending ->
        ImportDialog(
            pending = pending,
            onConfirm = onImportConfirmed,
            onDismiss = onImportCancelled,
        )
    }

    if (confirmingDelete) {
        DestructiveDialog(
            title = stringResource(R.string.settings_delete_title),
            body = stringResource(R.string.settings_delete_body),
            confirm = stringResource(R.string.settings_delete_confirm),
            onConfirm = {
                confirmingDelete = false
                onDeleteWorkoutData()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    if (confirmingReset) {
        DestructiveDialog(
            title = stringResource(R.string.settings_reset_title),
            body = stringResource(R.string.settings_reset_body),
            confirm = stringResource(R.string.settings_reset_confirm),
            onConfirm = {
                confirmingReset = false
                onResetApp()
            },
            onDismiss = { confirmingReset = false },
        )
    }

    state.message?.let { message ->
        MessageDialog(message = message, onDismiss = onMessageShown)
    }
}

/**
 * What an import would do, before it does it (§7).
 *
 * Counted rather than described: the only question worth answering before
 * overwriting the only copy of something is how much of it there is.
 */
@Composable
private fun ImportDialog(pending: PendingImport, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val preview = pending.preview
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                if (preview.isEmpty) {
                    Text(stringResource(R.string.settings_import_nothing))
                }
                if (preview.hasProfile) {
                    Text(
                        stringResource(
                            if (preview.replacesExistingProfile) {
                                R.string.settings_import_profile
                            } else {
                                R.string.settings_import_profile_new
                            },
                        ),
                    )
                }
                if (preview.newTemplates > 0) {
                    Text(stringResource(R.string.settings_import_new_plans, preview.newTemplates))
                }
                if (preview.replacedTemplates > 0) {
                    Text(
                        stringResource(
                            R.string.settings_import_replaced_plans,
                            preview.replacedTemplates,
                        ),
                    )
                }
                if (preview.sessions > 0) {
                    Text(stringResource(R.string.settings_import_sessions, preview.sessions))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !preview.isEmpty) {
                Text(stringResource(R.string.settings_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun DestructiveDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(body)
                // Offered at the moment it is useful rather than as a warning
                // nobody reads: this is the last screen before the data is gone.
                Text(
                    text = stringResource(R.string.settings_export_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun MessageDialog(message: SettingsMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        SettingsMessage.Exported -> stringResource(R.string.settings_exported)
        SettingsMessage.ExportFailed -> stringResource(R.string.settings_export_failed)
        SettingsMessage.Imported -> stringResource(R.string.settings_imported)
        SettingsMessage.WorkoutDataDeleted -> stringResource(R.string.settings_deleted)
        SettingsMessage.AppReset -> stringResource(R.string.settings_reset_done)
        is SettingsMessage.ImportRefused -> stringResource(
            when (message.failure) {
                is ImportFailure.Unreadable -> R.string.settings_import_unreadable
                is ImportFailure.WrongFormat -> R.string.settings_import_wrong_format
                is ImportFailure.TooNew -> R.string.settings_import_too_new
                is ImportFailure.Invalid -> R.string.settings_import_invalid
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Space.s4, bottom = Space.s1),
    )
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(labelOf(option))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // Colour is not the only signal: every destructive action here also
            // asks a question before doing anything.
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private const val MIME_JSON = "application/json"
