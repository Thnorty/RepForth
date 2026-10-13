package com.repforth.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.body.BodyRepository
import com.repforth.core.body.PhotoStore
import com.repforth.core.model.CheckIn
import com.repforth.core.model.Pose
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One check-in as a list row: when, how many figures, and a photo if it has one. */
data class CheckInRow(
    val id: String,
    val date: LocalDate,
    val measurementCount: Int,
    /** The first pose photographed, front before side before back. */
    val thumbnail: String?,
)

/** Newest first. */
internal fun checkInRows(checkIns: List<CheckIn>): List<CheckInRow> =
    checkIns
        .sortedByDescending { it.date }
        .map { checkIn ->
            CheckInRow(
                id = checkIn.id,
                date = checkIn.date,
                measurementCount = checkIn.measurements.size,
                thumbnail = Pose.entries.firstNotNullOfOrNull { checkIn.photos[it] },
            )
        }

@HiltViewModel
class CheckInListViewModel @Inject constructor(
    body: BodyRepository,
    private val photos: PhotoStore,
) : ViewModel() {

    /** Null until the first read, so the screen can tell loading from empty. */
    val rows: StateFlow<List<CheckInRow>?> = body.observeCheckIns()
        .map(::checkInRows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun photoFile(fileName: String): File = photos.fileFor(fileName)
}
