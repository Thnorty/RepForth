package com.repforth.feature.settings

import android.content.ContentResolver
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.model.ThemeMode
import com.repforth.core.testing.FakePreferencesStore
import com.repforth.core.transfer.DataTransfer
import com.repforth.core.transfer.ExportDocument
import com.repforth.core.transfer.ImportFailure
import com.repforth.core.transfer.ImportOutcome
import com.repforth.core.transfer.ImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.repforth.core.media.cache.MediaCacheManager
import java.io.File

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
    private lateinit var transfer: RecordingTransfer
    private lateinit var mediaCache: MediaCacheManager
    private lateinit var cacheDir: File
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = UserPreferencesDataSource(FakePreferencesStore())
        transfer = RecordingTransfer()
        cacheDir = File(System.getProperty("java.io.tmpdir"), "repforth_test_media_${System.currentTimeMillis()}")
        cacheDir.mkdirs()
        mediaCache = MediaCacheManager(cacheDir, dispatcher)
        viewModel = SettingsViewModel(preferences, transfer, NoContentResolver(), mediaCache)
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

    private fun state(): SettingsUiState = viewModel.uiState.value

    @Test
    fun `changing a preference stores it`() = runTest(dispatcher) {
        activate()
        viewModel.onThemeChange(ThemeMode.DARK)
        testScheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, preferences.preferences.first().themeMode)
    }

    /**
     * §7 requires a preview before an import, which is only a promise if
     * reading is genuinely separate from applying.
     */
    @Test
    fun `reading a file writes nothing until it is confirmed`() = runTest(dispatcher) {
        activate()
        transfer.nextRead = ImportOutcome.Ready(preview(newTemplates = 2), ExportDocument(exportedAt = 0))

        viewModel.onImportText(Result.success("{}"))
        testScheduler.advanceUntilIdle()

        assertEquals(2, state().pendingImport?.preview?.newTemplates)
        assertTrue("Reading must not import", transfer.imported.isEmpty())

        viewModel.onImportConfirmed()
        testScheduler.advanceUntilIdle()

        assertEquals(1, transfer.imported.size)
        assertNull("The pending file is consumed", state().pendingImport)
        assertEquals(SettingsMessage.Imported, state().message)
    }

    @Test
    fun `cancelling an import discards it without writing`() = runTest(dispatcher) {
        activate()
        transfer.nextRead = ImportOutcome.Ready(preview(), ExportDocument(exportedAt = 0))
        viewModel.onImportText(Result.success("{}"))
        testScheduler.advanceUntilIdle()

        viewModel.onImportCancelled()
        testScheduler.advanceUntilIdle()

        assertNull(state().pendingImport)
        assertTrue(transfer.imported.isEmpty())
    }

    @Test
    fun `a refused file says why, and nothing is pending`() = runTest(dispatcher) {
        activate()
        transfer.nextRead = ImportOutcome.Failed(ImportFailure.WrongFormat("something-else"))

        viewModel.onImportText(Result.success("{}"))
        testScheduler.advanceUntilIdle()

        val message = state().message
        assertTrue(message is SettingsMessage.ImportRefused)
        assertTrue(
            (message as SettingsMessage.ImportRefused).failure is ImportFailure.WrongFormat,
        )
        assertNull(state().pendingImport)
    }

    /**
     * A file that cannot even be opened must not read as a valid empty import.
     * The distinction matters: one is a broken file, the other is a file with
     * nothing in it, and only the second is safe to apply.
     */
    @Test
    fun `an unopenable file is refused rather than treated as empty`() = runTest(dispatcher) {
        activate()

        viewModel.onImportText(Result.failure(java.io.IOException("no such file")))
        testScheduler.advanceUntilIdle()

        val message = state().message
        assertTrue(message is SettingsMessage.ImportRefused)
        assertTrue(
            (message as SettingsMessage.ImportRefused).failure is ImportFailure.Unreadable,
        )
    }

    @Test
    fun `the two deletes are different actions`() = runTest(dispatcher) {
        activate()
        viewModel.onDeleteWorkoutData()
        testScheduler.advanceUntilIdle()

        assertEquals(1, transfer.workoutDeletes)
        assertEquals("Deleting workouts must not reset the app", 0, transfer.resets)
        assertEquals(SettingsMessage.WorkoutDataDeleted, state().message)

        viewModel.onMessageShown()
        testScheduler.advanceUntilIdle()
        viewModel.onResetApp()
        testScheduler.advanceUntilIdle()

        assertEquals(1, transfer.resets)
        assertEquals(SettingsMessage.AppReset, state().message)
    }

    @Test
    fun `a message is shown once`() = runTest(dispatcher) {
        activate()
        viewModel.onDeleteWorkoutData()
        testScheduler.advanceUntilIdle()
        assertEquals(SettingsMessage.WorkoutDataDeleted, state().message)

        viewModel.onMessageShown()
        testScheduler.advanceUntilIdle()

        assertNull(state().message)
    }

    @Test
    fun `clearing media cache empties cache directory and posts MediaCacheCleared message`() = runTest(dispatcher) {
        activate()
        // Create a dummy file in cache
        val sampleFile = File(cacheDir, "sample.bin")
        sampleFile.writeBytes(ByteArray(1024))
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
