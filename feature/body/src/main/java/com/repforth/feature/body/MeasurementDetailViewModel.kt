package com.repforth.feature.body

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.body.BodyRepository
import com.repforth.core.common.time.TimeSource
import com.repforth.core.designsystem.component.TrendPoint
import com.repforth.core.model.MeasurementType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** How far back the chart reaches. [months] null is everything. */
enum class TrendRange(val months: Long?) { THREE(3), SIX(6), YEAR(12), ALL(null) }

data class MeasurementEntry(val checkInId: String, val date: LocalDate, val value: Double)

data class MeasurementDetailUiState(
    val type: MeasurementType? = null,
    val range: TrendRange = TrendRange.THREE,
    /** The entries inside [range], for the chart. */
    val points: List<TrendPoint> = emptyList(),
    /** Every entry of the type, newest first, whatever the range. */
    val entries: List<MeasurementEntry> = emptyList(),
)

/** One measurement's history. The range narrows the chart, never the list. */
@HiltViewModel
class MeasurementDetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    body: BodyRepository,
    time: TimeSource,
    zone: ZoneId,
) : ViewModel() {

    private val typeId: String = checkNotNull(saved[TYPE_ID]) { "BodyMeasurement needs a typeId" }
    private val today: LocalDate = Instant.ofEpochMilli(time.now()).atZone(zone).toLocalDate()
    private val range = MutableStateFlow(TrendRange.THREE)

    val uiState: StateFlow<MeasurementDetailUiState> =
        combine(body.observeTypes(), body.observeCheckIns(), range) { types, checkIns, range ->
            val entries = checkIns
                .mapNotNull { checkIn ->
                    checkIn.measurements[typeId]?.let { MeasurementEntry(checkIn.id, checkIn.date, it) }
                }
                .sortedByDescending { it.date }
            val from = range.months?.let(today::minusMonths)
            MeasurementDetailUiState(
                type = types.firstOrNull { it.id == typeId },
                range = range,
                points = entries
                    .filter { from == null || !it.date.isBefore(from) }
                    .map { TrendPoint(it.date, it.value) },
                entries = entries,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementDetailUiState())

    fun onRange(range: TrendRange) {
        this.range.value = range
    }

    private companion object {
        const val TYPE_ID = "typeId"
    }
}
