package com.repforth.feature.body

import androidx.lifecycle.SavedStateHandle
import com.repforth.core.body.PhotoFailure
import com.repforth.core.body.PhotoImport
import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.model.CheckIn
import com.repforth.core.model.CheckInProblem
import com.repforth.core.model.Pose
import com.repforth.core.model.UnitSystem
import com.repforth.core.testing.FakeBodyRepository
import com.repforth.core.testing.FakePhotoStore
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repforth.core.testing.SCREENSHOT_SDK

/** Robolectric for `Uri`, which the photo calls take and the plain JVM cannot build. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [SCREENSHOT_SDK])
class CheckInViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val body = FakeBodyRepository(today = BodyFixtures.TODAY)
    private val photos = FakePhotoStore()

    /** 2026-10-13T12:00Z, the fixtures' today. */
    private val time = FakeTimeSource(wallClock = 1_791_892_800_000L)
    private val today = BodyFixtures.TODAY

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(saved: SavedStateHandle = SavedStateHandle()): CheckInViewModel {
        val viewModel = CheckInViewModel(saved, body, photos, time, ZoneOffset.UTC)
        advanceUntilIdle()
        return viewModel
    }

    private fun CheckInViewModel.field(typeId: String) = uiState.value.fields.single { it.type.id == typeId }

    @Test
    fun `a new check-in opens today's existing one`() = runTest(dispatcher) {
        body.save(CheckIn("existing", today, mapOf("waist" to 88.0), emptyMap()))

        val state = viewModel().uiState.value

        assertTrue(state.canDelete)
        assertEquals(88.0, state.fields.single { it.type.id == "waist" }.originalValue!!, 0.0)
    }

    @Test
    fun `tracked types are offered, and any type the check-in already holds`() = runTest(dispatcher) {
        body.save(CheckIn("existing", today, mapOf("neck" to 38.0), emptyMap()))

        val ids = viewModel().uiState.value.fields.map { it.type.id }

        assertEquals(listOf("weight", "body_fat", "neck", "waist"), ids)
    }

    /**
     * Stored 86.36 cm, shown as 34 in. Re-reading the shown "34" would store
     * 86.36 again only by luck of rounding; an untouched field is not re-read.
     */
    @Test
    fun `an untouched field keeps its stored value`() = runTest(dispatcher) {
        body.save(CheckIn("existing", today, mapOf("waist" to 86.3612), emptyMap()))
        val viewModel = viewModel()

        viewModel.onFieldChange("weight", "80")
        viewModel.onSave(UnitSystem.IMPERIAL)
        advanceUntilIdle()

        assertEquals(86.3612, body.find("existing")!!.measurements.getValue("waist"), 0.0)
    }

    @Test
    fun `a typed value is stored in canonical units`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onFieldChange("waist", "34")
        viewModel.onSave(UnitSystem.IMPERIAL)
        advanceUntilIdle()

        assertEquals(86.36, body.findOn(today)!!.measurements.getValue("waist"), 1e-9)
        assertTrue(viewModel.uiState.value.done)
    }

    @Test
    fun `a blank field removes that measurement`() = runTest(dispatcher) {
        body.save(CheckIn("existing", today, mapOf("waist" to 88.0, "weight" to 80.0), emptyMap()))
        val viewModel = viewModel()

        viewModel.onFieldChange("waist", "")
        viewModel.onSave(UnitSystem.METRIC)
        advanceUntilIdle()

        assertEquals(setOf("weight"), body.find("existing")!!.measurements.keys)
    }

    @Test
    fun `an unreadable field is named, and nothing is saved`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onFieldChange("waist", "8.8.8")
        viewModel.onSave(UnitSystem.METRIC)
        advanceUntilIdle()

        assertEquals("waist", viewModel.uiState.value.unreadableField)
        assertNull(body.findOn(today))
    }

    @Test
    fun `saving nothing new is refused as empty`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSave(UnitSystem.METRIC)
        advanceUntilIdle()

        assertEquals(CheckInProblem.EMPTY, viewModel.uiState.value.problem)
    }

    @Test
    fun `emptying a stored check-in asks to delete it`() = runTest(dispatcher) {
        body.save(CheckIn("existing", today, mapOf("waist" to 88.0), emptyMap()))
        val viewModel = viewModel()

        viewModel.onFieldChange("waist", "")
        viewModel.onSave(UnitSystem.METRIC)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.confirmingDelete)
        viewModel.onConfirmDelete()
        advanceUntilIdle()
        assertNull(body.find("existing"))
        assertTrue(viewModel.uiState.value.done)
    }

    @Test
    fun `saving onto a taken day shows the problem`() = runTest(dispatcher) {
        body.save(CheckIn("other", today.minusDays(1), mapOf("waist" to 88.0), emptyMap()))
        val viewModel = viewModel()

        viewModel.onDateChange(today.minusDays(1))
        viewModel.onFieldChange("weight", "80")
        viewModel.onSave(UnitSystem.METRIC)
        advanceUntilIdle()

        assertEquals(CheckInProblem.DATE_TAKEN, viewModel.uiState.value.problem)
        assertFalse(viewModel.uiState.value.done)
    }

    @Test
    fun `a picked photo fills its slot and is saved with the check-in`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onPhotoPicked(Pose.SIDE, android.net.Uri.EMPTY)
        advanceUntilIdle()
        viewModel.onSave(UnitSystem.METRIC)
        advanceUntilIdle()

        assertEquals(mapOf(Pose.SIDE to "photo-0.jpg"), body.findOn(today)!!.photos)
    }

    @Test
    fun `a capture survives process death`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved).newCapture(Pose.FRONT)

        // The camera app is open; the process dies; Android rebuilds the
        // screen from the saved state and delivers the camera's answer.
        val rebuilt = viewModel(saved)
        rebuilt.onCaptureResult(success = true)
        advanceUntilIdle()

        assertEquals(mapOf(Pose.FRONT to "photo-0.jpg"), rebuilt.uiState.value.photos)
    }

    @Test
    fun `a cancelled capture changes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.newCapture(Pose.FRONT)

        viewModel.onCaptureResult(success = false)
        advanceUntilIdle()

        assertEquals(emptyMap<Pose, String>(), viewModel.uiState.value.photos)
        assertFalse(viewModel.uiState.value.dirty)
    }

    @Test
    fun `an unreadable photo reports UNREADABLE and leaves the slot empty`() = runTest(dispatcher) {
        val viewModel = viewModel()
        photos.nextImport = PhotoImport.Failed(PhotoFailure.UNREADABLE)

        viewModel.onPhotoPicked(Pose.FRONT, android.net.Uri.EMPTY)
        advanceUntilIdle()

        assertEquals(PhotoFailure.UNREADABLE, viewModel.uiState.value.photoFailure)
        assertEquals(emptyMap<Pose, String>(), viewModel.uiState.value.photos)
    }

    @Test
    fun `discarding deletes photos this visit added and never saved`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onPhotoPicked(Pose.FRONT, android.net.Uri.EMPTY)
        advanceUntilIdle()

        viewModel.onDiscard()
        advanceUntilIdle()

        assertEquals(listOf("photo-0.jpg"), photos.deleted)
    }

    @Test
    fun `editing marks the screen dirty`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.dirty)

        viewModel.onFieldChange("waist", "88")

        assertTrue(viewModel.uiState.value.dirty)
    }
}
