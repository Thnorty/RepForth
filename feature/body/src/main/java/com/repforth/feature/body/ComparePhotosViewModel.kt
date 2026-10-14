package com.repforth.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.body.BodyRepository
import com.repforth.core.body.PhotoStore
import com.repforth.core.model.BuiltInMeasurement
import com.repforth.core.model.CheckIn
import com.repforth.core.model.Pose
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One side of the comparison: the day, its photo, and that day's weight if it was recorded. */
data class CompareSide(val date: LocalDate, val fileName: String, val weightKg: Double?)

data class ComparePhotosUiState(
    val pose: Pose,
    /** Every day with a photo of [pose], oldest first. */
    val dates: List<LocalDate>,
    /** Both null when fewer than two days hold [pose]: there is nothing to compare. */
    val left: CompareSide?,
    val right: CompareSide?,
)

/**
 * Two photos of one pose. Each side defaults to the earliest and the latest day
 * with that pose; a chosen day holds until the pose changes, which picks both
 * again because the chosen days may not have the new pose at all.
 */
@HiltViewModel
class ComparePhotosViewModel @Inject constructor(
    body: BodyRepository,
    private val photos: PhotoStore,
) : ViewModel() {

    private val choice = MutableStateFlow(Choice())

    /** Null until the first read. */
    val uiState: StateFlow<ComparePhotosUiState?> =
        combine(body.observeCheckIns(), choice) { checkIns, choice -> compareState(checkIns, choice) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPose(pose: Pose) {
        choice.value = Choice(pose = pose)
    }

    fun onLeftDate(date: LocalDate) {
        choice.value = choice.value.copy(left = date)
    }

    fun onRightDate(date: LocalDate) {
        choice.value = choice.value.copy(right = date)
    }

    fun photoFile(fileName: String): File = photos.fileFor(fileName)

    /** What the person picked. Null means "the default". */
    private data class Choice(val pose: Pose? = null, val left: LocalDate? = null, val right: LocalDate? = null)

    private fun compareState(checkIns: List<CheckIn>, choice: Choice): ComparePhotosUiState {
        fun withPose(pose: Pose) = checkIns.filter { pose in it.photos }.sortedBy { it.date }

        // Opened without a choice, land on a pose that can be compared rather
        // than on Front for someone who has only ever taken side photos.
        val pose = choice.pose ?: Pose.entries.firstOrNull { withPose(it).size >= 2 } ?: Pose.FRONT
        val days = withPose(pose)
        fun side(date: LocalDate?, default: CheckIn): CompareSide {
            val checkIn = days.firstOrNull { it.date == date } ?: default
            return CompareSide(
                date = checkIn.date,
                fileName = checkIn.photos.getValue(pose),
                weightKg = checkIn.measurements[BuiltInMeasurement.WEIGHT.id],
            )
        }
        val comparable = days.size >= 2
        return ComparePhotosUiState(
            pose = pose,
            dates = days.map { it.date },
            left = if (comparable) side(choice.left, days.first()) else null,
            right = if (comparable) side(choice.right, days.last()) else null,
        )
    }
}
