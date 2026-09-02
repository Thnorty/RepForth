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
import com.repforth.core.transfer.DataTransfer
import com.repforth.core.transfer.ExportDocument
import com.repforth.core.transfer.ImportFailure
import com.repforth.core.transfer.ImportOutcome
import com.repforth.core.transfer.ImportPreview
import com.repforth.core.userdata.ProfileRepository
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
    private lateinit var profileRepository: FakeProfileRepository
    private lateinit var transfer: RecordingTransfer
    private lateinit var mediaCache: MediaCacheManager
    private lateinit var cacheDir: File
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = UserPreferencesDataSource(FakePreferencesStore())
        profileRepository = FakeProfileRepository()
        transfer = RecordingTransfer()
        cacheDir = File(System.getProperty("java.io.tmpdir"), "repforth_test_media_${System.currentTimeMillis()}")
        cacheDir.mkdirs()
        mediaCache = MediaCacheManager(cacheDir, dispatcher)
        viewModel = SettingsViewModel(preferences, profileRepository, transfer, NoContentResolver(), mediaCache)
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
        val expected = preview(newTemplates = 3)
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

    private fun preview(newTemplates: Int = 0) = ImportPreview(
        hasProfile = false,
        replacesExistingProfile = false,
        newTemplates = newTemplates,
        replacedTemplates = 0,
        sessions = 0,
        exportedAt = 0,
    )
}

private class FakeProfileRepository : ProfileRepository {
    private val profileFlow = MutableStateFlow<UserProfile?>(
        UserProfile(
            id = "user-1",
            goal = TrainingGoal.STRENGTH,
            experience = ExperienceLevel.INTERMEDIATE,
            trainingDaysPerWeek = 4,
            sessionLengthMs = 45 * 60_000L,
            availableEquipment = setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL, Equipment.DUMBBELL),
            preferredMuscles = emptySet(),
            exclusions = emptySet(),
        ),
    )

    override fun observeProfile(): Flow<UserProfile?> = profileFlow

    override suspend fun getProfile(): UserProfile? = profileFlow.value

    override suspend fun save(profile: UserProfile) {
        profileFlow.value = profile
    }

    override suspend fun deleteAll() {
        profileFlow.value = null
    }
}

private class RecordingTransfer : DataTransfer {
    val imported = mutableListOf<ExportDocument>()
    var workoutDeletes = 0
    var resets = 0
    var nextRead: ImportOutcome = ImportOutcome.Failed(ImportFailure.Unreadable("not set"))

    override suspend fun export(): String = """{"format":"repforth.export"}"""

    override suspend fun read(json: String): ImportOutcome = nextRead

    override suspend fun import(document: ExportDocument) {
        imported += document
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
