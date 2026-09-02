package com.repforth.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.formatVolume
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.workout.ProgressSummary
import com.repforth.core.workout.WorkoutSummary
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The Progress tab (§12): history, streaks, volume, recent activity.
 *
 * Four figures and a list. §12 asks for numeric hierarchy, and this is the
 * screen where the numbers *are* the content — so they use the numeric styles
 * and the labels stay deliberately smaller than the figures they describe.
 */
@Composable
fun HistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreen(state = state, modifier = modifier)
}

@Composable
internal fun HistoryScreen(state: HistoryUiState, modifier: Modifier = Modifier) {
    if (state.isEmpty) {
        Column(
            modifier = modifier.fillMaxSize().padding(Layout.gutterPhone),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.progress_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.progress_empty_action),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        item(key = "summary") { ProgressPanel(state.progress) }

        if (state.mostPerformed.isNotEmpty()) {
            item(key = "most") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    SectionLabel(stringResource(R.string.progress_most_performed))
                    Text(
                        text = state.mostPerformed.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item(key = "history-label") {
            SectionLabel(stringResource(R.string.progress_history))
        }

        items(state.workouts, key = { it.sessionId }) { workout ->
            WorkoutRow(workout)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ProgressPanel(progress: ProgressSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            // Weighted and spaced, not pushed apart. Three unconstrained
            // columns in a SpaceBetween row have nowhere to go once their
            // labels grow: at 200% font scale in Turkish they ran together as
            // "AntrenmanBu haftaHaftalık seri", with no gap at all. Found by
            // the first screenshot ever taken of this screen.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Figure(
                    value = progress.workouts.toString(),
                    label = stringResource(R.string.progress_workouts),
                    modifier = Modifier.weight(1f),
                )
                Figure(
                    value = progress.workoutsThisWeek.toString(),
                    label = stringResource(R.string.progress_this_week),
                    modifier = Modifier.weight(1f),
                )
                Figure(
                    value = progress.streakWeeks.toString(),
                    label = stringResource(R.string.progress_streak),
                    modifier = Modifier.weight(1f),
                )
            }
            Figure(
                value = formatVolume(progress.totalVolumeKg),
                label = stringResource(R.string.progress_volume),
            )
        }
    }
}

/**
 * A number and what it means.
 *
 * The label is smaller than the figure on purpose: §12 says a label never
 * outranks the number it describes, and this screen is entirely numbers.
 */
@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(text = value, style = RepForthNumeric.md)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WorkoutRow(workout: WorkoutSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = formatDate(workout.startedAt), style = MaterialTheme.typography.bodyLarge)
            Text(
                // formatVolume already carries the unit; wrapping it in another
                // "%1$s kg" produced "0 kg kg".
                text = formatVolume(workout.volumeKg),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = buildString {
                append(
                    if (workout.setsSkipped > 0) {
                        stringResourceOf(workout)
                    } else {
                        pluralStringResource(
                            R.plurals.progress_sets,
                            workout.setsCompleted,
                            workout.setsCompleted,
                        )
                    },
                )
                workout.durationMs?.let {
                    append(" · ")
                    append(stringResource(R.string.progress_duration, (it / 60_000L).toInt()))
                }
                // An abandoned workout is still a workout, and saying so is more
                // useful than hiding it: the sets happened.
                if (!workout.completed) {
                    append(" · ")
                    append(stringResource(R.string.progress_abandoned))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun stringResourceOf(workout: WorkoutSummary): String = pluralStringResource(
    R.plurals.progress_sets_with_skipped,
    workout.setsCompleted,
    workout.setsCompleted,
    workout.setsSkipped,
)

/**
 * A total, in whatever unit the user reads, with its own symbol.
 *
 * The conversion and the threshold live in the design system so the builder and
 * the running workout cannot disagree with this screen about what a weight is.
 */
@Composable
private fun formatVolume(kg: Double): String {
    val units = LocalUnitSystem.current
    val (value, symbol) = units.formatVolume(kg)
    return NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
        .format(value.toDouble()) + " " + symbol
}

/**
 * The date, in the language the rest of the screen is in.
 *
 * `ofLocalizedDate` alone formats in `Locale.getDefault()` — the JVM's, which
 * is not necessarily the one this composition is rendering in. This app lets
 * the user choose a language independently of the system, and the first Turkish
 * screenshot of this screen came out with "Jan 1, 2026" sitting above "18 set ·
 * 45 dk". Reading the locale from the configuration is what every other
 * localised value here already does.
 */
@Composable
private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalConfiguration.current.locales[0])
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private const val TONNE_THRESHOLD_KG = 10_000.0
