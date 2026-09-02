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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.media.cache.MediaCacheManager
import com.repforth.core.exercisedata.detailRes
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.Language
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.TrainingGoal
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
    onOpenAiSettings: () -> Unit,
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
        onGoalChange = viewModel::onGoalChange,
        onExperienceChange = viewModel::onExperienceChange,
        onEquipmentChange = viewModel::onEquipmentChange,
        onThemeChange = viewModel::onThemeChange,
        onLanguageChange = viewModel::onLanguageChange,
        onUnitsChange = viewModel::onUnitsChange,
        onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
        onHapticsChange = viewModel::onHapticsChange,
        onReducedMotionChange = viewModel::onReducedMotionChange,
        onMediaWifiOnlyChange = viewModel::onMediaWifiOnlyChange,
        onClearMediaCache = viewModel::onClearMediaCache,
        onOpenAiSettings = onOpenAiSettings,
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
    onGoalChange: (TrainingGoal) -> Unit,
    onExperienceChange: (ExperienceLevel) -> Unit,
    onEquipmentChange: (Set<Equipment>) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (Language?) -> Unit,
    onUnitsChange: (UnitSystem) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onMediaWifiOnlyChange: (Boolean) -> Unit,
    onClearMediaCache: () -> Unit,
    onOpenAiSettings: () -> Unit,
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
    var confirmingClearMediaCache by rememberSaveable { mutableStateOf(false) }
    var editingEquipment by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        // One item, present from the first frame, whether or not the profile has
        // loaded yet.
        //
        // These were five keyed items inside `state.profile?.let`, and the
        // profile arrives one frame after the screen does — so five items were
        // *prepended* to a keyed LazyColumn that had already drawn. That is the
        // one case where keys work against you: the list faithfully kept the
        // item that was on screen ("Appearance") at the top, which put the whole
        // Profile section above the viewport. Settings opened already scrolled,
        // every time, and looked like a scroll-position bug rather than a
        // list-diffing one.
        //
        // Nothing is prepended now — the item's height changes instead, which a
        // list anchored at the top does not care about.
        item(key = "profile-section") {
            state.profile?.let { profile ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    SectionLabel(stringResource(R.string.settings_profile))

                    ChoiceRow(
                        label = stringResource(R.string.settings_profile_goal),
                        options = TrainingGoal.entries,
                        selected = profile.goal,
                        labelOf = { stringResource(it.labelRes) },
                        onSelected = onGoalChange,
                    )

                    ChoiceRow(
                        label = stringResource(R.string.settings_profile_experience),
                        options = ExperienceLevel.entries,
                        selected = profile.experience,
                        labelOf = { stringResource(it.labelRes) },
                        onSelected = onExperienceChange,
                    )

                    ActionRow(
                        label = stringResource(R.string.settings_profile_equipment),
                        detail = stringResource(
                            R.string.settings_profile_equipment_sub,
                            profile.availableEquipment.size,
                        ),
                        enabled = !state.busy,
                        onClick = { editingEquipment = true },
                    )

                    InfoRow(
                        label = stringResource(R.string.settings_profile_schedule),
                        detail = stringResource(
                            R.string.settings_profile_schedule_sub,
                            profile.trainingDaysPerWeek,
                            (profile.sessionLengthMs / 60_000L).toInt(),
                        ),
                    )
                }
            }
        }

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

        item(key = "media") { SectionLabel(stringResource(R.string.settings_media)) }

        item(key = "media-wifi-only") {
            SwitchRow(
                label = stringResource(R.string.settings_media_wifi_only),
                detail = stringResource(R.string.settings_media_wifi_only_sub),
                checked = state.preferences.mediaWifiOnly,
                onCheckedChange = onMediaWifiOnlyChange,
            )
        }

        item(key = "clear-media-cache") {
            // Both numbers are parameters now. The cap used to be typed into the
            // sentence -- "(250 MB cap)" -- in English and again in Turkish,
            // while the real ceiling lives in `MediaCacheManager`. Changing the
            // constant would have left two translations quietly lying about it.
            // The unit stays in the resource, where a translator can reach it.
            val usedMb = state.cacheSizeBytes / BYTES_PER_MB.toFloat()
            val capMb = (MediaCacheManager.DEFAULT_MAX_CACHE_BYTES / BYTES_PER_MB).toInt()
            ActionRow(
                label = stringResource(R.string.settings_clear_media_cache),
                detail = stringResource(R.string.settings_clear_media_cache_sub, usedMb, capMb),
                enabled = !state.busy,
                onClick = { confirmingClearMediaCache = true },
            )
        }

        item(key = "ai") { SectionLabel(stringResource(R.string.settings_ai)) }

        item(key = "ai-provider") {
            ActionRow(
                label = stringResource(R.string.settings_ai_row),
                detail = stringResource(R.string.settings_ai_row_sub),
                enabled = true,
                onClick = onOpenAiSettings,
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

    if (editingEquipment) {
        EquipmentDialog(
            selected = state.profile?.availableEquipment ?: emptySet(),
            onSave = onEquipmentChange,
            onDismiss = { editingEquipment = false },
        )
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

    if (confirmingClearMediaCache) {
        DestructiveDialog(
            title = stringResource(R.string.settings_clear_media_cache_title),
            body = stringResource(R.string.settings_clear_media_cache_body),
            confirm = stringResource(R.string.settings_clear_media_cache_confirm),
            onConfirm = {
                confirmingClearMediaCache = false
                onClearMediaCache()
            },
            onDismiss = { confirmingClearMediaCache = false },
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
                    Text(
                        pluralStringResource(
                            R.plurals.settings_import_new_plans,
                            preview.newTemplates,
                            preview.newTemplates,
                        ),
                    )
                }
                if (preview.replacedTemplates > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.settings_import_replaced_plans,
                            preview.replacedTemplates,
                            preview.replacedTemplates,
                        ),
                    )
                }
                // `ImportPreview` has counted weeks since export format 2 and
                // this dialog never showed them, so a file carrying five weeks
                // was described as though it carried none — on the one screen
                // whose whole job is saying what is about to be overwritten.
                if (preview.newWeeks > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.settings_import_new_weeks,
                            preview.newWeeks,
                            preview.newWeeks,
                        ),
                    )
                }
                if (preview.replacedWeeks > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.settings_import_replaced_weeks,
                            preview.replacedWeeks,
                            preview.replacedWeeks,
                        ),
                    )
                }
                if (preview.sessions > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.settings_import_sessions,
                            preview.sessions,
                            preview.sessions,
                        ),
                    )
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
private fun MessageDialog(message: SettingsMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        SettingsMessage.Exported -> stringResource(R.string.settings_exported)
        SettingsMessage.ExportFailed -> stringResource(R.string.settings_export_failed)
        SettingsMessage.Imported -> stringResource(R.string.settings_imported)
        SettingsMessage.WorkoutDataDeleted -> stringResource(R.string.settings_deleted)
        SettingsMessage.AppReset -> stringResource(R.string.settings_reset_done)
        SettingsMessage.MediaCacheCleared -> stringResource(R.string.settings_media_cache_cleared)
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
private fun InfoRow(label: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        // Both halves are weighted rather than pushed apart by SpaceBetween.
        // Two unconstrained Texts in a SpaceBetween row have nowhere to go when
        // they stop fitting: at 200% font scale "Schedule" and "3 days / week ·
        // 45 min" simply run past each other and off the screen. Weights let
        // them wrap instead, which is what `AGENTS.md` asks of every row that
        // holds text.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EquipmentDialog(
    selected: Set<Equipment>,
    onSave: (Set<Equipment>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(selected) }
    var moreEquipment by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profile_equipment_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                Equipment.COMMON.forEach { eq ->
                    val checked = eq in draft
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Target.min)
                            .clickable {
                                draft = if (checked) draft - eq else draft + eq
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                draft = if (isChecked) draft + eq else draft - eq
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(eq.labelRes), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(eq.detailRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                TextButton(onClick = { moreEquipment = !moreEquipment }) {
                    Text(
                        if (moreEquipment) {
                            stringResource(R.string.settings_profile_equipment_fewer)
                        } else {
                            stringResource(R.string.settings_profile_equipment_more, Equipment.UNCOMMON.size)
                        },
                    )
                }

                if (moreEquipment) {
                    Equipment.UNCOMMON.forEach { eq ->
                        val checked = eq in draft
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Target.min)
                                .clickable {
                                    draft = if (checked) draft - eq else draft + eq
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s3),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    draft = if (isChecked) draft + eq else draft - eq
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(eq.labelRes), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = stringResource(eq.detailRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(draft)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_profile_equipment_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

private const val MIME_JSON = "application/json"

/** One megabyte, for turning a byte count into the unit the screen prints. */
private const val BYTES_PER_MB = 1024L * 1024L
