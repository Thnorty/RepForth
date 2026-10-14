package com.repforth.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.body.BodyRepository
import com.repforth.core.body.PhotoStore
import com.repforth.core.model.CheckIn
import com.repforth.core.model.MeasurementType
import com.repforth.core.model.Pose
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A tracked measurement's newest value, and how far it moved since its previous one. */
data class LatestRow(val type: MeasurementType, val latest: Double, val change: Double?)

data class BodyUiState(
    val loading: Boolean = true,
    val rows: List<LatestRow> = emptyList(),
    val latestPhotos: Map<Pose, String> = emptyMap(),
    val canCompare: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && rows.isEmpty() && latestPhotos.isEmpty()
}

/**
 * The Body tab from what is stored.
 *
 * Each tracked type's change is against its own previous entry, not the
 * previous check-in: body fat measured monthly and weight weekly would
 * otherwise show no change for body fat most weeks. Compare needs two check-ins
 * sharing a pose, since it only ever sets a pose beside itself.
 */
internal fun bodyState(types: List<MeasurementType>, checkIns: List<CheckIn>): BodyUiState {
    val newestFirst = checkIns.sortedByDescending { it.date }
    val rows = types.sortedBy { it.sortOrder }.filter { it.tracked }.mapNotNull { type ->
        val values = newestFirst.mapNotNull { it.measurements[type.id] }
        val latest = values.firstOrNull() ?: return@mapNotNull null
        LatestRow(type, latest, values.getOrNull(1)?.let { latest - it })
    }
    val latestPhotos = Pose.entries.mapNotNull { pose ->
        newestFirst.firstNotNullOfOrNull { it.photos[pose] }?.let { pose to it }
    }.toMap()
    val canCompare = Pose.entries.any { pose -> checkIns.count { pose in it.photos } >= 2 }
    return BodyUiState(loading = false, rows = rows, latestPhotos = latestPhotos, canCompare = canCompare)
}

@HiltViewModel
class BodyViewModel @Inject constructor(
    body: BodyRepository,
    private val photos: PhotoStore,
) : ViewModel() {

    val uiState: StateFlow<BodyUiState> = combine(body.observeTypes(), body.observeCheckIns(), ::bodyState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState())

    init {
        // Once per process, here because this is the first screen that shows
        // photos: orphans from an abandoned check-in go before anyone sees one.
        viewModelScope.launch { body.sweepPhotosOnce() }
    }

    fun photoFile(fileName: String): File = photos.fileFor(fileName)
}
