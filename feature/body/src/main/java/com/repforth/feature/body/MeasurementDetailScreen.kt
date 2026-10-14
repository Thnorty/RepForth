package com.repforth.feature.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.component.RfChoiceChips
import com.repforth.core.designsystem.component.TrendChart
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.model.MeasurementType

/** One measurement: its trend over a chosen period, then every entry. */
@Composable
fun MeasurementDetailRoute(
    onOpenCheckIn: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasurementDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementDetailScreen(state, modifier, onRange = viewModel::onRange, onOpenCheckIn = onOpenCheckIn)
}

@Composable
internal fun MeasurementDetailScreen(
    state: MeasurementDetailUiState,
    modifier: Modifier = Modifier,
    onRange: (TrendRange) -> Unit = {},
    onOpenCheckIn: (String) -> Unit = {},
) {
    val type = state.type ?: return
    val name = type.displayName()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        item(key = "name") { Title(name, type.unit()) }
        item(key = "range") {
            RfChoiceChips(
                options = TrendRange.entries,
                selected = state.range,
                labelOf = { stringResource(it.labelRes) },
                onSelected = onRange,
            )
        }
        item(key = "chart") {
            if (state.points.isEmpty()) {
                Text(
                    text = stringResource(R.string.measurement_no_entries),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Chart(name, type, state)
            }
        }
        if (state.entries.isNotEmpty()) {
            item(key = "entries") { SectionLabel(stringResource(R.string.measurement_entries)) }
            items(state.entries, key = { it.checkInId }) { entry ->
                EntryRow(entry, type, onClick = { onOpenCheckIn(entry.checkInId) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** The name, with the unit beside it: the chart's own figures carry none. */
@Composable
private fun Title(name: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        if (unit.isNotEmpty()) {
            Text(unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chart(name: String, type: MeasurementType, state: MeasurementDetailUiState) {
    val sorted = state.points.sortedBy { it.date }
    val last = formatMeasurement(sorted.last().value, type)
    val description = if (sorted.size == 1) {
        stringResource(R.string.measurement_chart_single, name, last, formatCheckInDate(sorted.last().date))
    } else {
        stringResource(
            R.string.measurement_chart_description,
            name,
            formatMeasurement(sorted.first().value, type),
            last,
            stringResource(state.range.periodRes),
        )
    }
    val units = LocalUnitSystem.current
    val locale = LocalConfiguration.current.locales[0]
    TrendChart(
        points = state.points,
        formatValue = { measurementNumber(it, type.kind, units, locale) },
        formatDate = { checkInDateFormat(locale).format(it) },
        description = description,
    )
}

@Composable
private fun EntryRow(entry: MeasurementEntry, type: MeasurementType, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Text(
            text = formatCheckInDate(entry.date),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
            Text(formatMeasurementNumber(entry.value, type.kind), style = RepForthNumeric.xs)
            Text(
                text = type.unit(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TrendRange.labelRes: Int
    get() = when (this) {
        TrendRange.THREE -> R.string.measurement_range_three
        TrendRange.SIX -> R.string.measurement_range_six
        TrendRange.YEAR -> R.string.measurement_range_year
        TrendRange.ALL -> R.string.measurement_range_all
    }

private val TrendRange.periodRes: Int
    get() = when (this) {
        TrendRange.THREE -> R.string.measurement_period_three
        TrendRange.SIX -> R.string.measurement_period_six
        TrendRange.YEAR -> R.string.measurement_period_year
        TrendRange.ALL -> R.string.measurement_period_all
    }
