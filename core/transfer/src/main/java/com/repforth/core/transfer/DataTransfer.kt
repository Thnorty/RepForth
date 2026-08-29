package com.repforth.core.transfer

import com.repforth.core.common.time.TimeSource
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** The result of reading a file: either what it would do, or why it cannot. */
sealed interface ImportOutcome {
    data class Ready(val preview: ImportPreview, val document: ExportDocument) : ImportOutcome

    data class Failed(val failure: ImportFailure) : ImportOutcome
}

/**
 * Export, import, and the two kinds of delete (§7).
 *
 * There is no account and no backend, so the copy on this phone is the only
 * copy. Every method here is written from that fact: import never writes before
 * it has been shown what it would do, and the two deletes are separate
 * operations because "I am done with these workouts" and "I am done with this
 * app" are different intentions with different blast radii.
 */
interface DataTransfer {

    /** Everything the user made, as a versioned JSON document. */
    suspend fun export(): String

    /**
     * Reads and validates a file without writing anything.
     *
     * §7 requires a preview before import, which means parsing has to be a
     * separate step from applying. The parsed document is handed back so that
     * confirming does not re-read — a file that changed underneath between the
     * preview and the confirmation would otherwise apply something the user
     * never saw.
     */
    suspend fun read(json: String): ImportOutcome

    /** Applies a document already shown to the user by [read]. */
    suspend fun import(document: ExportDocument)

    /**
     * "Delete all workout data" (§7).
     *
     * Plans, sessions and the profile. The bundled catalog is untouched — it is
     * not the user's data, it is the app's, and re-downloading 1,324 exercises
     * because someone cleared their history would be a bug.
     */
    suspend fun deleteWorkoutData()

    /**
     * "Reset app" (§7).
     *
     * Everything [deleteWorkoutData] removes, plus preferences. AI settings,
     * encrypted keys and cached media are named by §7 and do not exist yet;
     * when they do they belong here, and `ResetCoverageTest` is what will say
     * so.
     */
    suspend fun resetApp()
}

internal class DefaultDataTransfer @Inject constructor(
    private val profiles: ProfileRepository,
    private val templates: TemplateRepository,
    private val sessions: SessionRepository,
    private val preferences: UserPreferencesDataSource,
    private val time: TimeSource,
) : DataTransfer {

    private val json = Json {
        prettyPrint = true
        // A file written by a newer version will have fields this build has
        // never heard of. Refusing to read it at all would be worse than
        // ignoring them; the version check above is what catches the cases
        // where the difference actually matters.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun export(): String = json.encodeToString(
        ExportDocument(
            exportedAt = time.now(),
            profile = profiles.getProfile()?.toDto(),
            templates = templates.observeAll().first().map { it.toDto() },
            sessions = sessions.observeFinished().first().map { it.toDto() },
        ),
    )

    override suspend fun read(json: String): ImportOutcome {
        val document = try {
            this.json.decodeFromString<ExportDocument>(json)
        } catch (e: Exception) {
            return ImportOutcome.Failed(
                ImportFailure.Unreadable(e.message ?: "could not be parsed"),
            )
        }

        if (document.format != ExportDocument.FORMAT) {
            return ImportOutcome.Failed(ImportFailure.WrongFormat(document.format))
        }
        if (document.version > ExportDocument.VERSION) {
            return ImportOutcome.Failed(
                ImportFailure.TooNew(document.version, ExportDocument.VERSION),
            )
        }

        // Convert everything now, so that a file which parses but cannot be
        // turned into a valid plan fails before the user is told it will work.
        // The domain's own invariants are the validation; there is no second
        // set of rules here to disagree with them.
        try {
            document.profile?.toDomain()
            document.templates.forEach { it.toDomain() }
            document.sessions.forEach { it.toDomain() }
        } catch (e: Exception) {
            return ImportOutcome.Failed(
                ImportFailure.Invalid(e.message ?: "contained something invalid"),
            )
        }

        val existingIds = templates.observeAll().first().map { it.id }.toSet()
        val incomingIds = document.templates.map { it.id }
        return ImportOutcome.Ready(
            preview = ImportPreview(
                hasProfile = document.profile != null,
                replacesExistingProfile = document.profile != null && profiles.getProfile() != null,
                newTemplates = incomingIds.count { it !in existingIds },
                replacedTemplates = incomingIds.count { it in existingIds },
                sessions = document.sessions.size,
                exportedAt = document.exportedAt,
            ),
            document = document,
        )
    }

    override suspend fun import(document: ExportDocument) {
        document.profile?.let { profiles.save(it.toDomain()) }
        document.templates.forEach { templates.save(it.toDomain()) }
        document.sessions.forEach { sessions.persist(it.toDomain()) }
    }

    override suspend fun deleteWorkoutData() {
        sessions.deleteAll()
        templates.deleteAll()
        profiles.deleteAll()
    }

    override suspend fun resetApp() {
        deleteWorkoutData()
        preferences.clear()
    }
}
