package com.repforth.feature.body

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.body.BodyRepository
import com.repforth.core.body.PhotoFailure
import com.repforth.core.body.PhotoImport
import com.repforth.core.body.PhotoStore
import com.repforth.core.body.SaveCheckIn
import com.repforth.core.common.time.TimeSource
import com.repforth.core.model.CheckIn
import com.repforth.core.model.CheckInProblem
import com.repforth.core.model.MeasurementType
import com.repforth.core.model.Pose
import com.repforth.core.model.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One measurement's field.
 *
 * [text] is null until the person types. An untouched field saves
 * [originalValue] as stored, never re-read from the text shown for it — the
 * shown text is rounded and in the reader's units, so re-reading it would move
 * a stored 86.36 cm a little on every save.
 */
data class FieldState(val type: MeasurementType, val text: String?, val originalValue: Double?)

data class CheckInUiState(
    val loading: Boolean = true,
    val id: String = "",
    val date: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val fields: List<FieldState> = emptyList(),
    val photos: Map<Pose, String> = emptyMap(),
    /** Poses whose photo is still being shrunk and stored; Save waits for them. */
    val importing: Set<Pose> = emptySet(),
    val problem: CheckInProblem? = null,
    /** The type id whose text is not a number, when that is what stopped a save. */
    val unreadableField: String? = null,
    val photoFailure: PhotoFailure? = null,
    /** Editing a stored check-in, so there is something to delete. */
    val canDelete: Boolean = false,
    val dirty: Boolean = false,
    val confirmingDelete: Boolean = false,
    val done: Boolean = false,
)

/**
 * A check-in, new or stored.
 *
 * Opened without an id it edits today's, if there is one: one check-in per day,
 * so "Check in" twice on a day means "change today's". Everything the person
 * has done here -- typed values, photos added or removed, the photo being taken
 * -- is kept in [SavedStateHandle]: Android is most likely to kill this process
 * while the camera app is in front, and the form must be as they left it.
 */
@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val body: BodyRepository,
    private val photos: PhotoStore,
    private val time: TimeSource,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private var storedPhotos: Map<Pose, String> = emptyMap()

    /** Photo changes this visit: a file name, or null for removed. Applied over [storedPhotos]. */
    private val photoEdits: MutableMap<Pose, String?> =
        saved.get<HashMap<String, String>>(PHOTO_EDITS).orEmpty()
            .mapKeys { Pose.valueOf(it.key) }
            .mapValues { it.value.ifEmpty { null } }
            .toMutableMap()

    /** Files imported this visit, which are orphans unless a save keeps them. */
    private val added: MutableSet<String> = saved.get<ArrayList<String>>(ADDED).orEmpty().toMutableSet()

    /** Typed text by type id, for the fields that have been touched. */
    private val typed: MutableMap<String, String> = saved.get<HashMap<String, String>>(TYPED).orEmpty().toMutableMap()

    private fun keepPhotos() {
        saved[PHOTO_EDITS] = HashMap(photoEdits.entries.associate { it.key.name to it.value.orEmpty() })
        saved[ADDED] = ArrayList(added)
    }

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val today = Instant.ofEpochMilli(time.now()).atZone(zone).toLocalDate()
        val types = body.observeTypes().first()
        val requested = saved.get<String>(ARG_ID)
        val existing = if (requested != null) body.find(requested) else body.findOn(today)
        val id = existing?.id ?: saved.get<String>(NEW_ID) ?: UUID.randomUUID().toString().also { saved[NEW_ID] = it }
        storedPhotos = existing?.photos.orEmpty()

        val fields = types.sortedBy { it.sortOrder }
            .filter { it.tracked || existing?.measurements?.containsKey(it.id) == true }
            .map { FieldState(it, text = typed[it.id], originalValue = existing?.measurements?.get(it.id)) }

        _uiState.update {
            it.copy(
                loading = false,
                id = id,
                date = saved.get<Long>(DATE)?.let(LocalDate::ofEpochDay) ?: existing?.date ?: today,
                today = today,
                fields = fields,
                photos = shownPhotos(),
                canDelete = existing != null,
                // Rebuilt after process death with edits in hand: still unsaved.
                dirty = typed.isNotEmpty() || photoEdits.isNotEmpty() || saved.contains(DATE),
            )
        }
    }

    private fun shownPhotos(): Map<Pose, String> =
        Pose.entries.mapNotNull { pose ->
            val name = if (pose in photoEdits) photoEdits[pose] else storedPhotos[pose]
            name?.let { pose to it }
        }.toMap()

    fun onDateChange(date: LocalDate) {
        saved[DATE] = date.toEpochDay()
        _uiState.update { it.copy(date = date, dirty = true, problem = null) }
    }

    fun onFieldChange(typeId: String, text: String) {
        typed[typeId] = text
        saved[TYPED] = HashMap(typed)
        _uiState.update { state ->
            state.copy(
                fields = state.fields.map { if (it.type.id == typeId) it.copy(text = text) else it },
                dirty = true,
                problem = null,
                unreadableField = null,
            )
        }
    }

    fun onPhotoPicked(pose: Pose, uri: Uri?) {
        if (uri == null) return
        importing(pose) { photos.importFrom(uri) }
    }

    /** Where the camera app should write a photo for [pose]. */
    fun newCapture(pose: Pose): Uri {
        val capture = photos.newCapture()
        saved[CAPTURE_POSE] = pose.name
        saved[CAPTURE_PATH] = capture.path
        return capture.uri
    }

    fun onCaptureResult(success: Boolean) {
        val pose = saved.get<String>(CAPTURE_POSE)?.let(Pose::valueOf) ?: return
        val path = saved.get<String>(CAPTURE_PATH) ?: return
        saved.remove<String>(CAPTURE_POSE)
        saved.remove<String>(CAPTURE_PATH)
        if (!success) {
            File(path).delete()
            return
        }
        importing(pose) { photos.importCapture(path) }
    }

    /** Runs an import with [pose] marked as in progress, so Save cannot overtake it. */
    private fun importing(pose: Pose, import: suspend () -> PhotoImport) {
        _uiState.update { it.copy(importing = it.importing + pose) }
        viewModelScope.launch {
            try {
                receive(pose, import())
            } finally {
                _uiState.update { it.copy(importing = it.importing - pose) }
            }
        }
    }

    private fun receive(pose: Pose, result: PhotoImport) {
        when (result) {
            is PhotoImport.Stored -> {
                added += result.fileName
                photoEdits[pose] = result.fileName
                keepPhotos()
                _uiState.update { it.copy(photos = shownPhotos(), dirty = true, photoFailure = null, problem = null) }
            }
            is PhotoImport.Failed -> _uiState.update { it.copy(photoFailure = result.reason) }
        }
    }

    fun onRemovePhoto(pose: Pose) {
        photoEdits[pose] = null
        keepPhotos()
        _uiState.update { it.copy(photos = shownPhotos(), dirty = true) }
    }

    fun onSave(units: UnitSystem) {
        val state = _uiState.value
        // A photo still being added would be left out of what is saved.
        if (state.loading || state.importing.isNotEmpty()) return
        val measurements = mutableMapOf<String, Double>()
        for (field in state.fields) {
            val value = when {
                field.text == null -> field.originalValue
                field.text.isBlank() -> null
                else -> readMeasurement(field.text, field.type.kind, units)
                    ?: return _uiState.update { it.copy(unreadableField = field.type.id) }
            }
            value?.let { measurements[field.type.id] = it }
        }

        if (measurements.isEmpty() && state.photos.isEmpty()) {
            _uiState.update {
                if (it.canDelete) it.copy(confirmingDelete = true) else it.copy(problem = CheckInProblem.EMPTY)
            }
            return
        }

        val checkIn = CheckIn(state.id, state.date, measurements, state.photos)
        viewModelScope.launch {
            when (val result = body.save(checkIn)) {
                is SaveCheckIn.Saved -> {
                    discardUnused(keep = checkIn.photos.values.toSet())
                    _uiState.update { it.copy(done = true) }
                }
                is SaveCheckIn.Refused -> _uiState.update { it.copy(problem = result.problem) }
            }
        }
    }

    fun onAskDelete() = _uiState.update { it.copy(confirmingDelete = true) }

    fun onCancelDelete() = _uiState.update { it.copy(confirmingDelete = false) }

    fun onConfirmDelete() {
        val id = _uiState.value.id
        viewModelScope.launch {
            body.delete(id)
            discardUnused(keep = emptySet())
            _uiState.update { it.copy(confirmingDelete = false, done = true) }
        }
    }

    /** Leaving without saving: the photos imported this visit belong to nothing. */
    fun onDiscard() {
        viewModelScope.launch { discardUnused(keep = emptySet()) }
    }

    private suspend fun discardUnused(keep: Set<String>) {
        val unused = added - keep
        if (unused.isNotEmpty()) photos.delete(unused)
        added.clear()
        keepPhotos()
    }

    fun photoFile(fileName: String): File = photos.fileFor(fileName)

    private companion object {
        /** The route's argument: `Destination.BodyCheckIn.checkInId`. */
        const val ARG_ID = "checkInId"
        const val NEW_ID = "newCheckInId"
        const val DATE = "date"
        const val CAPTURE_POSE = "capturePose"
        const val CAPTURE_PATH = "capturePath"
        const val TYPED = "typed"
        const val PHOTO_EDITS = "photoEdits"
        const val ADDED = "added"
    }
}
