package com.repforth.feature.settings

import android.content.ContentResolver
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.media.cache.MediaCacheManager
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutLimits
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.testing.FakeProfiles
import com.repforth.core.testing.sampleProfile
import com.repforth.core.transfer.DataTransfer
import com.repforth.core.transfer.ExportDocument
import com.repforth.core.transfer.ImportFailure
import com.repforth.core.transfer.ImportOutcome
import com.repforth.core.transfer.ImportPreview
import com.repforth.core.transfer.ImportResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Settings, minus the file picker.
 *
 * The picker is the system's and needs a device; what is testable here is
 * everything either side of it — that reading a file writes nothing until it is
 * confirmed, that a refused file says why, and that the two deletes stay
 * different from each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var preferences: UserPreferencesDataSource
    private lateinit var profileRepository: FakeProfiles
    private lateinit var transfer: RecordingTransfer
    private lateinit var mediaCache: MediaCacheManager
    private lateinit var exercises: FakeExercises
    private lateinit var cacheDir: File
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = UserPreferencesDataSource(FakePreferencesStore())
        profileRepository = FakeProfiles(
            sampleProfile(
                availableEquipment = setOf(
                    Equipment.BODY_WEIGHT,
                    Equipment.BARBELL,
                    Equipment.DUMBBELL,
                ),
            ),
        )
        transfer = RecordingTransfer()
        cacheDir = File(System.getProperty("java.io.tmpdir"), "repforth_test_media_${System.currentTimeMillis()}")
        cacheDir.mkdirs()
        mediaCache = MediaCacheManager(cacheDir, dispatcher)
        exercises = FakeExercises()
        viewModel = SettingsViewModel(
            preferences, profileRepository, transfer, NoContentResolver(), mediaCache, exercises,
        )
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    /**
     * Starts collecting, and returns the latest state.
     *
     * `uiState` is a `WhileSubscribed` flow: with nothing collecting it, its
     * value never leaves the initial one and every assertion here reads a state
     * the ViewModel never produced. The collector lives in `backgroundScope` so
     * `runTest` cancels it rather than waiting on it.
     */
    private fun TestScope.activate() {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
    }

    private fun state() = viewModel.uiState.value

    @Test
    fun `profile reflects repository state and updates on goal, experience and equipment changes`() = runTest(dispatcher) {
        activate()
        assertNotNull(state().profile)
        assertEquals(TrainingGoal.STRENGTH, state().profile?.goal)
        assertEquals(ExperienceLevel.INTERMEDIATE, state().profile?.experience)
        assertEquals(setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL, Equipment.DUMBBELL), state().profile?.availableEquipment)

        viewModel.onGoalChange(TrainingGoal.HYPERTROPHY)
        testScheduler.advanceUntilIdle()
        assertEquals(TrainingGoal.HYPERTROPHY, state().profile?.goal)

        viewModel.onExperienceChange(ExperienceLevel.ADVANCED)
        testScheduler.advanceUntilIdle()
        assertEquals(ExperienceLevel.ADVANCED, state().profile?.experience)

        val newEquipment = setOf(Equipment.BODY_WEIGHT, Equipment.KETTLEBELL)
        viewModel.onEquipmentChange(newEquipment)
        testScheduler.advanceUntilIdle()
        assertEquals(newEquipment, state().profile?.availableEquipment)
    }

    /**
     * The schedule was written by onboarding and by nothing else.
     *
     * Settings showed it as a read-only row, so someone whose training time
     * changed had to reset the app and lose their history to say so — and
     * `sessionLengthMs` is the entire budget Coach programmes a day against.
     */
    @Test
    fun `the schedule can be changed after onboarding`() = runTest(dispatcher) {
        activate()
        assertEquals(4, state().profile?.trainingDaysPerWeek)
        assertEquals(45 * 60_000L, state().profile?.sessionLengthMs)

        viewModel.onScheduleChange(daysPerWeek = 6, sessionMinutes = 75)
        testScheduler.advanceUntilIdle()

        assertEquals(6, state().profile?.trainingDaysPerWeek)
        assertEquals(75 * 60_000L, state().profile?.sessionLengthMs)
    }

    /**
     * The control cannot offer an illegal value, so one arriving is a
     * programming error — and refusing to save would be a worse answer than
     * saving the nearest legal thing.
     */
    @Test
    fun `a schedule outside the allowed range is clamped rather than refused`() =
        runTest(dispatcher) {
            activate()

            viewModel.onScheduleChange(daysPerWeek = 99, sessionMinutes = 5)
            testScheduler.advanceUntilIdle()

            assertEquals(WorkoutLimits.days.last, state().profile?.trainingDaysPerWeek)
            assertEquals(
                WorkoutLimits.sessionMinutes.first * 60_000L,
                state().profile?.sessionLengthMs,
            )
        }

    @Test
    fun `preferences reflect changes from the view model`() = runTest(dispatcher) {
        activate()
        assertEquals(ThemeMode.SYSTEM, state().preferences.themeMode)

        viewModel.onThemeChange(ThemeMode.DARK)
        testScheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, state().preferences.themeMode)
        assertEquals(ThemeMode.DARK, preferences.preferences.first().themeMode)
    }

    @Test
    fun `a valid export document is presented as ready to import`() = runTest(dispatcher) {
        activate()
        val expected = preview(templates = 3)
        val document = ExportDocument(exportedAt = 0L, profile = null, templates = emptyList(), sessions = emptyList())
        transfer.nextRead = ImportOutcome.Ready(expected, document)

        viewModel.onImportText(Result.success("{}"))
        testScheduler.advanceUntilIdle()

        val pending = state().pendingImport
        assertNotNull("The file was ready to import, so pendingImport must be set", pending)
        assertEquals(expected, pending?.preview)
        assertEquals(document, pending?.document)
        assertNull("A valid file does not show an error message", state().message)
    }

    @Test
    fun `confirming an import applies it and clears the pending document`() = runTest(dispatcher) {
        activate()
        val document = ExportDocument(exportedAt = 0L, profile = null, templates = emptyList(), sessions = emptyList())
        transfer.nextRead = ImportOutcome.Ready(preview(), document)
        viewModel.onImportText(Result.success("{}"))
        testScheduler.advanceUntilIdle()

        viewModel.onImportConfirmed()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(document), transfer.imported)
        assertNull(state().pendingImport)
        assertEquals(SettingsMessage.Imported, state().message)
    }

    /**
     * An import that failed says so, and stops saying it is busy.
     *
     * The result used to be discarded: `import` returned nothing and this
     * reported `Imported.` whatever had happened. Import replaces everything, so
     * that message over a failed write is the worst possible thing to be wrong
     * about — the user has just been told their old data is gone and their new
     * data arrived, and neither is true.
     */
    @Test
    fun `an import that could not be saved says so`() = runTest(dispatcher) {
        activate()
        val document = ExportDocument(exportedAt = 0L)
        transfer.nextRead = ImportOutcome.Ready(preview(), document)
        transfer.nextImport = ImportResult.Failed(ImportFailure.NotApplied("disk full"))
        viewModel.onImportText(Result.success("{}"))
        testScheduler.advanceUntilIdle()

        viewModel.onImportConfirmed()
        testScheduler.advanceUntilIdle()

        assertEquals(
            SettingsMessage.ImportRefused(ImportFailure.NotApplied("disk full")),
            state().message,
        )
        assertEquals("The screen must not be left spinning", false, state().busy)
        assertNull(state().pendingImport)
    }

    @Test
    fun `cancelling an import clears the pending document without applying it`() = runTest(dispatcher) {
        activate()
        transfer.nextRead = ImportOutcome.Ready(
            preview(),
            ExportDocument(exportedAt = 0L, profile = null, templates = emptyList(), sessions = emptyList()),
        )
        viewModel.onImportText(Result.success("{}"))
        testScheduler.advanceUntilIdle()

        viewModel.onImportCancelled()
        testScheduler.advanceUntilIdle()

        assertTrue("Cancelled imports must not be applied", transfer.imported.isEmpty())
        assertNull(state().pendingImport)
    }

    @Test
    fun `a corrupt file is refused with its error and no pending document is stored`() = runTest(dispatcher) {
        activate()
        transfer.nextRead = ImportOutcome.Failed(ImportFailure.Invalid("truncated"))

        viewModel.onImportText(Result.success("{not-json"))
        testScheduler.advanceUntilIdle()

        assertNull(state().pendingImport)
        val message = state().message as? SettingsMessage.ImportRefused
        assertNotNull(message)
        assertTrue(message?.failure is ImportFailure.Invalid)
    }

    @Test
    fun `a file that failed to open is refused as unreadable`() = runTest(dispatcher) {
        activate()

        viewModel.onImportText(Result.failure(RuntimeException("permission denied")))
        testScheduler.advanceUntilIdle()

        assertNull(state().pendingImport)
        val message = state().message as? SettingsMessage.ImportRefused
        assertNotNull(message)
        assertTrue(message?.failure is ImportFailure.Unreadable)
    }

    @Test
    fun `deleting workout data asks the repository and posts a message`() = runTest(dispatcher) {
        activate()

        viewModel.onDeleteWorkoutData()
        testScheduler.advanceUntilIdle()

        assertEquals(1, transfer.workoutDeletes)
        assertEquals(SettingsMessage.WorkoutDataDeleted, state().message)
    }

    @Test
    fun `resetting the app asks the repository and posts a message`() = runTest(dispatcher) {
        activate()

        viewModel.onResetApp()
        testScheduler.advanceUntilIdle()

        assertEquals(1, transfer.resets)
        assertEquals(SettingsMessage.AppReset, state().message)
    }

    @Test
    fun `clearing media cache invokes MediaCacheManager and posts a message`() = runTest(dispatcher) {
        activate()
        File(cacheDir, "sample.bin").writeBytes(ByteArray(1024))
        mediaCache.calculateCacheSize()
        testScheduler.advanceUntilIdle()

        viewModel.onClearMediaCache()
        testScheduler.advanceUntilIdle()

        assertEquals(SettingsMessage.MediaCacheCleared, state().message)
        assertEquals(0L, state().cacheSizeBytes)
    }

    @Test
    fun `toggling media wifi only setting updates preferences`() = runTest(dispatcher) {
        activate()
        assertEquals(true, state().preferences.mediaWifiOnly)

        viewModel.onMediaWifiOnlyChange(false)
        testScheduler.advanceUntilIdle()

        assertEquals(false, state().preferences.mediaWifiOnly)
    }

    // ---- Editable exclusions (§3, §8) ----

    /**
     * The three kinds share one field, so each editor has to leave the others.
     *
     * This is the whole hazard. `UserProfile.exclusions` is a single set and
     * `MovementExclusion` carries its own kind, so an editor that wrote only
     * what it knows about would delete every excluded exercise the moment a
     * muscle was ticked — and there are now three screens writing this field.
     */
    @Test
    fun `editing muscles leaves excluded exercises and movements alone`() = runTest(dispatcher) {
        activate()
        profileRepository.save(
            profileRepository.getProfile()!!.copy(
                exclusions = setOf(
                    MovementExclusion(ExclusionKind.MUSCLE, Muscle.PECTORALS.slug),
                    MovementExclusion(ExclusionKind.EXERCISE, "ex-1"),
                    MovementExclusion(ExclusionKind.MOVEMENT, "overhead pressing"),
                ),
            ),
        )

        viewModel.onExcludedMusclesChange(setOf(Muscle.LATS))
        testScheduler.advanceUntilIdle()

        val stored = profileRepository.getProfile()!!.exclusions
        assertEquals(
            "The muscle is replaced",
            setOf(Muscle.LATS.slug),
            stored.filter { it.kind == ExclusionKind.MUSCLE }.map { it.value }.toSet(),
        )
        assertEquals(
            "The excluded exercise must survive a muscle edit",
            setOf("ex-1"),
            stored.filter { it.kind == ExclusionKind.EXERCISE }.map { it.value }.toSet(),
        )
        assertEquals(
            "And so must the movement",
            setOf("overhead pressing"),
            stored.filter { it.kind == ExclusionKind.MOVEMENT }.map { it.value }.toSet(),
        )
    }

    @Test
    fun `editing movements leaves muscles and exercises alone`() = runTest(dispatcher) {
        activate()
        profileRepository.save(
            profileRepository.getProfile()!!.copy(
                exclusions = setOf(
                    MovementExclusion(ExclusionKind.MUSCLE, Muscle.PECTORALS.slug),
                    MovementExclusion(ExclusionKind.EXERCISE, "ex-1"),
                ),
            ),
        )

        viewModel.onMovementExclusionsChange(listOf("deep knee flexion"))
        testScheduler.advanceUntilIdle()

        val stored = profileRepository.getProfile()!!.exclusions
        assertEquals(3, stored.size)
        assertEquals(
            setOf("deep knee flexion"),
            stored.filter { it.kind == ExclusionKind.MOVEMENT }.map { it.value }.toSet(),
        )
        assertEquals(1, stored.count { it.kind == ExclusionKind.MUSCLE })
        assertEquals(1, stored.count { it.kind == ExclusionKind.EXERCISE })
    }

    @Test
    fun `blank and duplicate movements are dropped rather than stored`() = runTest(dispatcher) {
        activate()

        viewModel.onMovementExclusionsChange(listOf("  press  ", "press", "", "   "))
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf("press"),
            profileRepository.getProfile()!!.exclusions.map { it.value },
        )
    }

    /**
     * The counter and the rules engine are the same rule.
     *
     * A count produced by a second copy of the matching would be a count that
     * can disagree with the thing it describes, which is worse than no count.
     * `movementExcludes` is shared for exactly this, and this is the assertion
     * that the editor is actually using it.
     */
    @Test
    fun `the movement counter reports what the rule would exclude`() = runTest(dispatcher) {
        activate()
        exercises.names = listOf(
            "Barbell Bench Press",
            "Dumbbell Shoulder Press",
            "Overhead Press",
            "Barbell Squat",
        )

        viewModel.onMovementEditorOpened()
        testScheduler.advanceUntilIdle()

        assertEquals("Three names contain it, case-insensitively", 3, state().movementMatches("press"))
        assertEquals(1, state().movementMatches("overhead"))
        assertEquals("Nothing matches, which is worth saying", 0, state().movementMatches("burpee"))
    }

    @Test
    fun `the counter loads the catalog once`() = runTest(dispatcher) {
        activate()
        viewModel.onMovementEditorOpened()
        testScheduler.advanceUntilIdle()

        exercises.names = listOf("Something else entirely")
        viewModel.onMovementEditorOpened()
        testScheduler.advanceUntilIdle()

        assertEquals(
            "The catalog is read-only and cannot change while Settings is open",
            4,
            state().catalogNames.size,
        )
    }

    @Test
    fun `preferred muscles are editable and do not touch exclusions`() = runTest(dispatcher) {
        activate()
        profileRepository.save(
            profileRepository.getProfile()!!.copy(
                exclusions = setOf(MovementExclusion(ExclusionKind.EXERCISE, "ex-1")),
            ),
        )

        viewModel.onPreferredMusclesChange(setOf(Muscle.LATS))
        testScheduler.advanceUntilIdle()

        val profile = profileRepository.getProfile()!!
        assertEquals(setOf(Muscle.LATS), profile.preferredMuscles)
        assertEquals(1, profile.exclusions.size)
    }

    @Test
    fun `removing one excluded exercise leaves the rest`() = runTest(dispatcher) {
        activate()
        profileRepository.save(
            profileRepository.getProfile()!!.copy(
                exclusions = setOf(
                    MovementExclusion(ExclusionKind.EXERCISE, "ex-1"),
                    MovementExclusion(ExclusionKind.EXERCISE, "ex-2"),
                    MovementExclusion(ExclusionKind.MUSCLE, Muscle.LATS.slug),
                ),
            ),
        )

        viewModel.onExcludedExerciseRemoved("ex-1")
        testScheduler.advanceUntilIdle()

        val stored = profileRepository.getProfile()!!.exclusions
        assertEquals(
            setOf("ex-2"),
            stored.filter { it.kind == ExclusionKind.EXERCISE }.map { it.value }.toSet(),
        )
        assertEquals(1, stored.count { it.kind == ExclusionKind.MUSCLE })
    }

    private fun preview(templates: Int = 0) = ImportPreview(
        hasProfile = false,
        templates = templates,
        weeks = 0,
        sessions = 0,
        exportedAt = 0,
        removesProfile = false,
        removedTemplates = 0,
        removedWeeks = 0,
        removedSessions = 0,
    )
}


private class RecordingTransfer : DataTransfer {
    val imported = mutableListOf<ExportDocument>()
    var workoutDeletes = 0
    var resets = 0
    var nextRead: ImportOutcome = ImportOutcome.Failed(ImportFailure.Unreadable("not set"))

    override suspend fun export(): String = """{"format":"repforth.export"}"""

    override suspend fun read(json: String): ImportOutcome = nextRead

    /** What the next import answers. Applied unless a test says otherwise. */
    var nextImport: ImportResult = ImportResult.Applied

    override suspend fun import(document: ExportDocument): ImportResult {
        imported += document
        return nextImport
    }

    override suspend fun deleteWorkoutData() {
        workoutDeletes++
    }

    override suspend fun resetApp() {
        resets++
    }
}

/**
 * A ContentResolver that is never asked for a stream.
 *
 * Robolectric would give a real one; this project has no Android test runtime,
 * and the tests that matter here never reach the resolver — reading a file is
 * the system's job and the device's test.
 */
private class NoContentResolver : ContentResolver(null)

/**
 * Just enough catalog for the movement editor's counter.
 *
 * Names only: that is all `movementExcludes` reads, and a fake that answered
 * every other question would be pretending this screen asks them.
 */
private class FakeExercises : ExerciseRepository {
    var names: List<String> = listOf(
        "Barbell Bench Press",
        "Dumbbell Shoulder Press",
        "Overhead Press",
        "Barbell Squat",
    )

    override suspend fun count(): Int = names.size

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> =
        MutableStateFlow(emptyList())

    override suspend fun find(id: ExerciseId): Exercise? = null

    override suspend fun summaries(ids: Collection<ExerciseId>): Map<ExerciseId, ExerciseSummary> =
        emptyMap()

    override suspend fun candidates(): List<ExerciseCandidate> = names.mapIndexed { index, name ->
        ExerciseCandidate(
            id = ExerciseId("ex-$index"),
            name = name,
            bodyPart = BodyPart.CHEST,
            target = Muscle.PECTORALS,
            muscleGroup = Muscle.PECTORALS,
            secondaryMuscles = emptySet(),
            equipment = Equipment.BARBELL,
        )
    }
}
