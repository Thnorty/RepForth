package com.repforth.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.model.Language
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.UnitSystem
import com.repforth.core.model.UserPreferences
import com.repforth.core.transfer.DataTransfer
import com.repforth.core.transfer.ExportDocument
import com.repforth.core.transfer.ImportFailure
import com.repforth.core.transfer.ImportOutcome
import com.repforth.core.transfer.ImportPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Something to tell the user once, after an action they took. */
sealed interface SettingsMessage {
    data object Exported : SettingsMessage

    data object ExportFailed : SettingsMessage

    data object Imported : SettingsMessage

    data object WorkoutDataDeleted : SettingsMessage

    data object AppReset : SettingsMessage

    data class ImportRefused(val failure: ImportFailure) : SettingsMessage
}

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences.Default,
    /** Set when a file has been read and is waiting to be confirmed. */
    val pendingImport: PendingImport? = null,
    val message: SettingsMessage? = null,
    val busy: Boolean = false,
)

/**
 * A file that has been read but not applied.
 *
 * §7 requires a preview before an import, so the parsed document is carried
 * alongside what it would do. Re-reading on confirmation would risk applying
 * something other than what was shown.
 */
data class PendingImport(val preview: ImportPreview, val document: ExportDocument)

/**
 * Settings (§7, §12).
 *
 * Two kinds of thing live here and they are not alike. The preferences are
 * immediate and reversible — flip the theme, flip it back. The data actions are
 * neither: they touch the only copy of everything the user has recorded, so each
 * one asks first, and the two deletes stay separate because being done with
 * some workouts and being done with the app are different intentions.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesDataSource,
    private val transfer: DataTransfer,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(preferences.preferences, local) { stored, state ->
            state.copy(preferences = stored)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(),
        )

    fun onThemeChange(mode: ThemeMode) = edit { preferences.setThemeMode(mode) }

    fun onLanguageChange(language: Language?) = edit { preferences.setLanguage(language) }

    fun onUnitsChange(system: UnitSystem) = edit { preferences.setUnitSystem(system) }

    fun onKeepScreenOnChange(enabled: Boolean) = edit { preferences.setKeepScreenOn(enabled) }

    fun onHapticsChange(enabled: Boolean) = edit { preferences.setHapticsEnabled(enabled) }

    fun onReducedMotionChange(enabled: Boolean) = edit { preferences.setReducedMotion(enabled) }

    /**
     * Writes the export to wherever the system file picker put it.
     *
     * The document is produced first and written second, so a failure to write
     * cannot leave a half-file behind: `openOutputStream` truncates, and a
     * truncated file that looks like an export is worse than no file.
     */
    fun onExportTo(uri: Uri) {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true)
            val json = transfer.export()
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("no output stream")
                }
            }
            local.value = local.value.copy(
                busy = false,
                message = if (written.isSuccess) {
                    SettingsMessage.Exported
                } else {
                    SettingsMessage.ExportFailed
                },
            )
        }
    }

    /**
     * Reads and validates a chosen file. Writes nothing until confirmed.
     *
     * Split from [onImportText] because a `Uri` only exists on a device — it is
     * a stubbed class on the JVM — so everything worth testing lives on the
     * other side of this line and this stays thin enough to read.
     */
    fun onImportFrom(uri: Uri) {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true)
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("no input stream")
                }
            }
            onImportText(text)
        }
    }

    /**
     * A file's contents, read and previewed.
     *
     * A failure to open is refused as unreadable rather than treated as an
     * empty document: one is a broken file and the other is a file with nothing
     * in it, and only the second would be safe to apply.
     */
    internal suspend fun onImportText(text: Result<String>) {
        val outcome = text.fold(
            onSuccess = { transfer.read(it) },
            onFailure = {
                ImportOutcome.Failed(
                    ImportFailure.Unreadable(it.message ?: "could not be opened"),
                )
            },
        )

        local.value = when (outcome) {
            is ImportOutcome.Ready -> local.value.copy(
                busy = false,
                pendingImport = PendingImport(outcome.preview, outcome.document),
            )

            is ImportOutcome.Failed -> local.value.copy(
                busy = false,
                message = SettingsMessage.ImportRefused(outcome.failure),
            )
        }
    }

    fun onImportConfirmed() {
        val pending = local.value.pendingImport ?: return
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, pendingImport = null)
            transfer.import(pending.document)
            local.value = local.value.copy(busy = false, message = SettingsMessage.Imported)
        }
    }

    fun onImportCancelled() {
        local.value = local.value.copy(pendingImport = null)
    }

    fun onDeleteWorkoutData() {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true)
            transfer.deleteWorkoutData()
            local.value = local.value.copy(
                busy = false,
                message = SettingsMessage.WorkoutDataDeleted,
            )
        }
    }

    fun onResetApp() {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true)
            transfer.resetApp()
            local.value = local.value.copy(busy = false, message = SettingsMessage.AppReset)
        }
    }

    /** Messages are shown once; this is how the screen says it has. */
    fun onMessageShown() {
        local.value = local.value.copy(message = null)
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
