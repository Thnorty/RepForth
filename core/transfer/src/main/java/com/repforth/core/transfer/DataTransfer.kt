package com.repforth.core.transfer

import com.repforth.core.ai.ProviderRepository
import com.repforth.core.common.time.TimeSource
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.UserDataTransaction
import com.repforth.core.workout.SessionPhase
import com.repforth.core.userdata.WeekRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The result of applying a document. Nothing changed unless it is [Applied]. */
sealed interface ImportResult {
    data object Applied : ImportResult

    data class Failed(val failure: ImportFailure) : ImportResult
}

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

    /**
     * Applies a document already shown to the user by [read].
     *
     * **Replaces, rather than merges.** The workout data afterwards is exactly
     * the workout data in the file: the profile, the standalone plans, the
     * weekly plans and the history are all cleared first. A file is a snapshot
     * of a phone, and restoring one is a restore — merging would have to decide,
     * silently and per row, what to do about a session that exists in both
     * copies and differs.
     *
     * All of it happens in one transaction, so a failure leaves the database
     * exactly as it was. That is reported rather than thrown: overwriting the
     * only copy of somebody's training history is not an operation whose result
     * a caller may forget to check.
     */
    suspend fun import(document: ExportDocument): ImportResult

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
     * Everything [deleteWorkoutData] removes, plus preferences, the AI
     * provider settings, and every stored provider key. Cached media is named
     * by §7 too and does not exist yet; when it does it belongs here.
     *
     * `ResetCoverageTest` fails if a store is added to this class and not
     * cleared here. That guard exists because the failure is silent: a user who
     * resets the app and hands the phone on has no way to discover that their
     * API key is still in it.
     */
    suspend fun resetApp()
}

internal class DefaultDataTransfer(
    private val profiles: ProfileRepository,
    private val templates: TemplateRepository,
    private val weeks: WeekRepository,
    private val sessions: SessionRepository,
    private val preferences: UserPreferencesDataSource,
    private val providers: ProviderRepository,
    private val transaction: UserDataTransaction,
    private val time: TimeSource,
    /**
     * Parsing and serialising a whole training history is not main-thread work.
     *
     * Confined here rather than at each call site, following
     * `MediaCacheManager`: a caller that forgets is a dropped frame on the one
     * screen where the user is already waiting, and every caller would have to
     * remember separately.
     */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DataTransfer {

    /**
     * What Hilt builds. The dispatcher is a seam for tests, not a binding.
     *
     * Following `MediaCacheManager`: a default argument on an `@Inject`
     * constructor is not a default as far as Dagger is concerned — it asks for a
     * `CoroutineDispatcher` binding and fails the build when there is none.
     */
    @Inject
    constructor(
        profiles: ProfileRepository,
        templates: TemplateRepository,
        weeks: WeekRepository,
        sessions: SessionRepository,
        preferences: UserPreferencesDataSource,
        providers: ProviderRepository,
        transaction: UserDataTransaction,
        time: TimeSource,
    ) : this(
        profiles, templates, weeks, sessions, preferences, providers, transaction, time,
        Dispatchers.IO,
    )

    private val json = Json {
        prettyPrint = true
        // A file written by a newer version will have fields this build has
        // never heard of. Refusing to read it at all would be worse than
        // ignoring them; the version check above is what catches the cases
        // where the difference actually matters.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun export(): String = withContext(io) {
        json.encodeToString(
            ExportDocument(
                exportedAt = time.now(),
                profile = profiles.getProfile()?.toDto(),
                // Standalone workouts only — `observeAll()` filters out any that
                // belong to a week, and those travel inside their week below.
                templates = templates.observeAll().first().map { it.toDto() },
                weeks = weeks.observeAll().first().map { it.toDto() },
                sessions = sessions.observeFinished().first().map { it.toDto() },
            ),
        )
    }

    override suspend fun read(json: String): ImportOutcome {
        val document = withContext(io) {
            try {
                Result.success(this@DefaultDataTransfer.json.decodeFromString<ExportDocument>(json))
            } catch (e: Exception) {
                Result.failure<ExportDocument>(e)
            }
        }.getOrElse { e ->
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
        // The domain's own invariants are most of the validation; there is no
        // second set of rules here to disagree with them.
        //
        // What the domain cannot see is the file as a whole — it validates one
        // plan at a time and has no opinion about two of them claiming the same
        // id, or about a session the export should never have contained. Those
        // are checked alongside it, and both matter more now that importing
        // replaces everything: there is no previous copy left to fall back on.
        try {
            document.profile?.toDomain()
            document.templates.forEach { it.toDomain() }
            document.weeks.forEach { it.toDomain() }
            document.sessions.forEach { it.toDomain() }
            document.checkIdentities()
            document.checkSessionsAreFinished()
        } catch (e: Exception) {
            return ImportOutcome.Failed(
                ImportFailure.Invalid(e.message ?: "contained something invalid"),
            )
        }

        return ImportOutcome.Ready(
            preview = ImportPreview(
                hasProfile = document.profile != null,
                templates = document.templates.size,
                weeks = document.weeks.size,
                sessions = document.sessions.size,
                exportedAt = document.exportedAt,
                // Everything currently stored, because everything currently
                // stored is what goes. Counted here rather than described as
                // "replaced", which was only ever true of the rows whose ids
                // happened to match.
                removesProfile = profiles.getProfile() != null,
                removedTemplates = templates.observeAll().first().size,
                removedWeeks = weeks.observeAll().first().size,
                removedSessions = sessions.observeFinished().first().size,
            ),
            document = document,
        )
    }

    override suspend fun import(document: ExportDocument): ImportResult = try {
        transaction.run {
            // Clear first, then write. This is what makes the import a restore
            // rather than a merge: no row survives that the file does not
            // contain, so importing the same file twice lands in the same place
            // and importing an older one does not leave newer records behind
            // pretending to belong to it.
            deleteWorkoutData()

            document.profile?.let { profiles.save(it.toDomain()) }
            document.templates.forEach { templates.save(it.toDomain()) }
            // Saving a week writes its day templates too, so these must not also
            // be saved through `templates`, and the export never puts them there.
            document.weeks.forEach { weeks.save(it.toDomain()) }
            document.sessions.forEach { sessions.persist(it.toDomain()) }
        }
        ImportResult.Applied
    } catch (e: Exception) {
        // The transaction rolled back, so this is a report about a database
        // that is unchanged. Saying nothing here is what let a half-applied
        // import look like a finished one.
        ImportResult.Failed(ImportFailure.NotApplied(e.message ?: "could not be saved"))
    }

    override suspend fun deleteWorkoutData() {
        sessions.deleteAll()
        templates.deleteAll()
        weeks.deleteAll()
        profiles.deleteAll()
    }

    override suspend fun resetApp() {
        deleteWorkoutData()
        preferences.clear()
        providers.deleteAll()
    }
}

/**
 * Two ids for one thing, anywhere in the file.
 *
 * The domain validates a plan at a time and cannot see this: each of two
 * templates sharing an id is individually valid, and importing them writes one
 * over the other, so the file quietly describes fewer plans than it lists. The
 * same collision inside a session is worse — set records are keyed by exercise
 * id and position, so a duplicate exercise id merges two exercises' sets into
 * one and the count still looks plausible.
 *
 * Checked across templates and weeks together, because a week's days become
 * `workout_template` rows like any other and share the id space with them.
 */
private fun ExportDocument.checkIdentities() {
    val templateIds = templates.map { it.id } + weeks.flatMap { week -> week.days.map { it.workout.id } }
    templateIds.firstDuplicate()?.let { throw IllegalArgumentException("Two plans share the id \"$it\"") }

    weeks.map { it.id }.firstDuplicate()?.let {
        throw IllegalArgumentException("Two weeks share the id \"$it\"")
    }
    sessions.map { it.id }.firstDuplicate()?.let {
        throw IllegalArgumentException("Two workouts share the id \"$it\"")
    }

    (templates + weeks.flatMap { week -> week.days.map { it.workout } }).forEach { template ->
        template.exercises.map { it.id }.firstDuplicate()?.let {
            throw IllegalArgumentException("\"${template.name}\" lists the exercise row \"$it\" twice")
        }
    }
    sessions.forEach { session ->
        session.exercises.map { it.id }.firstDuplicate()?.let {
            throw IllegalArgumentException("A workout lists the exercise row \"$it\" twice")
        }
        session.exercises.forEach { exercise ->
            exercise.outcomes.map { it.position }.firstDuplicate()?.let {
                throw IllegalArgumentException("A workout records set $it twice")
            }
        }
    }
}

/**
 * A session in the file has to be one that finished.
 *
 * The export writes `observeFinished()`, so `COMPLETED` and `ABANDONED` are the
 * only phases it can legitimately contain. `SessionDto.toDomain` accepts any
 * phase, which meant a hand-edited or third-party file could install a workout
 * that claims to be `RESTING` — and the file carries no cursor and no deadline,
 * so there is nothing to resume it from. The app would find an active session
 * on next launch, offer to continue it, and land on a workout with no position.
 *
 * Refused rather than coerced to `ABANDONED`: silently rewriting what a record
 * says happened is not this code's decision to make.
 */
private fun ExportDocument.checkSessionsAreFinished() {
    sessions.forEach { session ->
        val phase = SessionPhase.entries.firstOrNull { it.name == session.phase }
            ?: throw IllegalArgumentException("Unknown session phase: \"${session.phase}\"")
        require(phase.isTerminal) {
            "A workout in this file is still \"${session.phase}\"; an export only contains finished ones"
        }
    }
}

/** The first value that appears twice, or null. */
private fun <T> List<T>.firstDuplicate(): T? {
    val seen = mutableSetOf<T>()
    return firstOrNull { !seen.add(it) }
}
