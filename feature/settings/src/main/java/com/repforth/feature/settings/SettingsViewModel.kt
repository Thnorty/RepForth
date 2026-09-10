package com.repforth.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.Language
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UnitSystem
import com.repforth.core.model.WorkoutLimits
import com.repforth.core.model.UserPreferences
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.Muscle
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.UserProfile
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.transfer.DataTransfer
import com.repforth.core.transfer.ExportDocument
import com.repforth.core.transfer.ImportFailure
import com.repforth.core.transfer.ImportOutcome
import com.repforth.core.transfer.ImportPreview
import com.repforth.core.transfer.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.repforth.core.media.cache.MediaCacheManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Something to tell the user once, after an action they took. */
sealed interface SettingsMessage {
    data object Exported : SettingsMessage

    data object ExportFailed : SettingsMessage

    data object Imported : SettingsMessage

    data object WorkoutDataDeleted : SettingsMessage

    data object AppReset : SettingsMessage

    data object MediaCacheCleared : SettingsMessage

    data class ImportRefused(val failure: ImportFailure) : SettingsMessage
}

data class SettingsUiState(
    /**
     * Null until the stored preferences arrive, and never a guess.
     *
     * **This defaulted to `UserPreferences.Default` and it was visibly wrong.**
     * Reported from a phone: with vibration and sound turned off, reopening
     * Settings drew both switches with the thumb over at the "on" side and the
     * "off" colours. Their defaults are `true`, so the screen drew a switch it
     * had guessed, then corrected it a frame later -- and a Material switch
     * resolves its colours from the new value immediately while animating its
     * thumb and its size to match. The colours arrived and the movement did
     * not, leaving a control that said two things at once.
     *
     * The three preferences whose default happened to match what was stored
     * looked fine throughout, which is what made it read as a bug about
     * vibration and sound rather than about guessing.
     */
    val preferences: UserPreferences? = null,
    val profile: UserProfile? = null,
    val cacheSizeBytes: Long = 0L,
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
    private val profileRepository: ProfileRepository,
    private val transfer: DataTransfer,
    private val contentResolver: ContentResolver,
    private val mediaCache: MediaCacheManager,
    // For the movement editor's counter, and nothing else on this screen.
    private val exercises: ExerciseRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(preferences.preferences, profileRepository.observeProfile(), mediaCache.cacheSize, local) { stored, userProfile, cacheSize, state ->
            state.copy(preferences = stored, profile = userProfile, cacheSizeBytes = cacheSize)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(),
        )

    fun onGoalChange(goal: TrainingGoal) {
        viewModelScope.launch {
            val current = profileRepository.getProfile() ?: return@launch
            profileRepository.save(current.copy(goal = goal))
        }
    }

    fun onExperienceChange(experience: ExperienceLevel) {
        viewModelScope.launch {
            val current = profileRepository.getProfile() ?: return@launch
            profileRepository.save(current.copy(experience = experience))
        }
    }

    /**
     * How often and how long this person trains.
     *
     * Written only by onboarding until now, and shown in Settings as a read-only
     * row — so someone whose training time changed had to reset the app and lose
     * their history to say so. It is the most consequential field of the four in
     * this section, because `sessionLengthMs` is the entire budget Coach
     * programmes a day against.
     */
    fun onScheduleChange(daysPerWeek: Int, sessionMinutes: Int) {
        viewModelScope.launch {
            val current = profileRepository.getProfile() ?: return@launch
            profileRepository.save(
                current.copy(
                    trainingDaysPerWeek = daysPerWeek.coerceIn(WorkoutLimits.days),
                    sessionLengthMs = sessionMinutes
                        .coerceIn(WorkoutLimits.sessionMinutes)
                        .toLong() * 60_000L,
                ),
            )
        }
    }

    fun onEquipmentChange(equipment: Set<Equipment>) {
        viewModelScope.launch {
            val current = profileRepository.getProfile() ?: return@launch
            profileRepository.save(current.copy(availableEquipment = equipment))
        }
    }

    private fun editProfile(block: (UserProfile) -> UserProfile) {
        viewModelScope.launch {
            val current = profileRepository.getProfile() ?: return@launch
            profileRepository.save(block(current))
        }
    }

    fun onThemeChange(mode: ThemeMode) = edit { preferences.setThemeMode(mode) }

    fun onLanguageChange(language: Language?) = edit { preferences.setLanguage(language) }

    fun onUnitsChange(system: UnitSystem) = edit { preferences.setUnitSystem(system) }

    fun onKeepScreenOnChange(enabled: Boolean) = edit { preferences.setKeepScreenOn(enabled) }

    fun onHapticsChange(enabled: Boolean) = edit { preferences.setHapticsEnabled(enabled) }

    fun onSoundChange(enabled: Boolean) = edit { preferences.setSoundEnabled(enabled) }

    fun onReducedMotionChange(enabled: Boolean) = edit { preferences.setReducedMotion(enabled) }

    fun onMediaWifiOnlyChange(enabled: Boolean) = edit { preferences.setMediaWifiOnly(enabled) }

    fun onClearMediaCache() {
        viewModelScope.launch {
            local.value = local.value.copy(busy = true)
            mediaCache.clearCache()
            local.value = local.value.copy(
                busy = false,
                message = SettingsMessage.MediaCacheCleared,
            )
        }
    }

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
                    contentResolver.openInputStream(uri)?.use { it.readBounded() }
                        ?: error("no input stream")
                }
            }
            val outcome = text.exceptionOrNull()
                ?.let { it as? FileTooLarge }
                ?.let { ImportOutcome.Failed(ImportFailure.TooLarge(it.bytes, MAX_IMPORT_BYTES)) }
            if (outcome != null) {
                local.value = local.value.copy(
                    busy = false,
                    message = SettingsMessage.ImportRefused(outcome.failure),
                )
            } else {
                onImportText(text)
            }
        }
    }

    /**
     * The file, or a refusal, without pulling an arbitrary file into memory.
     *
     * `readBytes()` allocates whatever the stream hands it, and the stream is
     * whatever the user tapped in a file picker. An export of several years of
     * training is a few megabytes; the limit is far above that and far below
     * anything that would take the app down with it.
     *
     * Measured while reading rather than asked for in advance. A content
     * provider is not obliged to report a size, and the ones that do are not
     * obliged to be right — so the bound has to be on the bytes actually taken.
     */
    private fun InputStream.readBounded(): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > MAX_IMPORT_BYTES) throw FileTooLarge(buffer.size().toLong())
        }
        return buffer.toByteArray().decodeToString()
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

    /**
     * Applies the document the user has just been shown.
     *
     * The result is read rather than assumed. Import replaces everything, and it
     * used to report success unconditionally — so a write that failed halfway
     * said "Imported." over a database holding part of one export and part of
     * another. It is one transaction now, so a failure here is a database that
     * did not change, and the message says so.
     */
    fun onImportConfirmed() {
        val pending = local.value.pendingImport ?: return
        viewModelScope.launch {
            local.value = local.value.copy(busy = true, pendingImport = null)
            val result = transfer.import(pending.document)
            local.value = local.value.copy(
                busy = false,
                message = when (result) {
                    is ImportResult.Applied -> SettingsMessage.Imported
                    is ImportResult.Failed -> SettingsMessage.ImportRefused(result.failure)
                },
            )
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
            mediaCache.clearCache()
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

/**
 * A file bigger than any export this app writes.
 *
 * 32 MB against a few megabytes for years of training. Generous on purpose: the
 * bound exists so that tapping the wrong file in a picker cannot exhaust memory,
 * not to police how much someone has trained.
 */
internal const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

/** Thrown while reading, so the size shows up in the refusal. */
private class FileTooLarge(val bytes: Long) : Exception("file is $bytes bytes")

