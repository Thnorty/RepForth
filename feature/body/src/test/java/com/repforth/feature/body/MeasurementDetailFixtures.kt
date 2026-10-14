package com.repforth.feature.body

import com.repforth.core.designsystem.component.TrendPoint

/** Eight waist entries ten days apart, newest first, ending today at 88 cm. */
internal object MeasurementDetailFixtures {

    private val waist = BodyFixtures.TYPES.single { it.id == "waist" }

    private val entries = listOf(88.0, 88.5, 89.0, 89.5, 89.5, 90.0, 90.5, 91.0).mapIndexed { index, value ->
        MeasurementEntry("w$index", BodyFixtures.TODAY.minusDays(index * 10L), value)
    }

    val WAIST = MeasurementDetailUiState(
        type = waist,
        range = TrendRange.THREE,
        points = entries.map { TrendPoint(it.date, it.value) },
        entries = entries,
    )
}
