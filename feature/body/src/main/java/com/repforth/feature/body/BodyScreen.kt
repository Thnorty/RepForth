package com.repforth.feature.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.model.Pose
import java.io.File
import kotlin.math.abs

/**
 * Progress, Body: the latest of each tracked measurement, the latest photos,
 * and the way into everything else.
 */
@Composable
fun BodyRoute(
    onCheckIn: () -> Unit,
    onOpenMeasurement: (String) -> Unit,
    onCompare: () -> Unit,
    onAllCheckIns: () -> Unit,
    onChooseTracked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BodyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BodyScreen(
        state = state,
        modifier = modifier,
        photoFile = viewModel::photoFile,
        onCheckIn = onCheckIn,
        onOpenMeasurement = onOpenMeasurement,
        onCompare = onCompare,
        onAllCheckIns = onAllCheckIns,
        onChooseTracked = onChooseTracked,
    )
}

@Composable
internal fun BodyScreen(
    state: BodyUiState,
    modifier: Modifier = Modifier,
    photoFile: (String) -> File? = { null },
    onCheckIn: () -> Unit = {},
    onOpenMeasurement: (String) -> Unit = {},
    onCompare: () -> Unit = {},
    onAllCheckIns: () -> Unit = {},
    onChooseTracked: () -> Unit = {},
) {
    if (state.loading) return
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isEmpty) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(Layout.gutterPhone),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.body_empty),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                // Before the first check-in too: otherwise arms, hips and the
                // rest can only be chosen after saving one without them.
                SecondaryButton(
                    stringResource(R.string.body_choose_tracked),
                    onChooseTracked,
                    Modifier.padding(top = Space.s4),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                if (state.rows.isNotEmpty()) {
                    item(key = "latest") { SectionLabel(stringResource(R.string.body_latest)) }
                    items(state.rows, key = { it.type.id }) { row ->
                        LatestValueRow(row, onClick = { onOpenMeasurement(row.type.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (state.latestPhotos.isNotEmpty()) {
                    item(key = "photos") {
                        PhotoStrip(state.latestPhotos, state.canCompare, photoFile, onCompare)
                    }
                }
                item(key = "links") {
                    Column(
                        modifier = Modifier.padding(top = Space.s3),
                        verticalArrangement = Arrangement.spacedBy(Space.s2),
                    ) {
                        SecondaryButton(stringResource(R.string.body_all_check_ins), onAllCheckIns)
                        SecondaryButton(stringResource(R.string.body_choose_tracked), onChooseTracked)
                    }
                }
            }
        }
        Button(
            onClick = onCheckIn,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s3)
                .heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.body_check_in))
        }
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Name on the left; the figure, its unit and its change on the right. */
@Composable
private fun LatestValueRow(row: LatestRow, onClick: () -> Unit) {
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
            text = row.type.displayName(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                Text(formatMeasurementNumber(row.latest, row.type.kind), style = RepForthNumeric.sm)
                Text(
                    text = row.type.unit(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.change?.let { ChangeText(it, row) }
        }
    }
}

/**
 * The change, in a neutral colour: a smaller waist and a bigger arm are both
 * good, so the app does not colour a direction. The arrow is seen; the words
 * are what TalkBack reads.
 */
@Composable
private fun ChangeText(change: Double, row: LatestRow) {
    val amount = formatMeasurementNumber(abs(change), row.type.kind)
    val none = amount == formatMeasurementNumber(0.0, row.type.kind)
    val shown = when {
        none -> stringResource(R.string.body_change_none)
        change < 0 -> stringResource(R.string.body_change_down, amount)
        else -> stringResource(R.string.body_change_up, amount)
    }
    val spoken = when {
        none -> stringResource(R.string.body_change_none_spoken)
        change < 0 -> stringResource(R.string.body_change_down_spoken, amount)
        else -> stringResource(R.string.body_change_up_spoken, amount)
    }
    Text(
        text = shown,
        style = RepForthNumeric.xs,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

@Composable
private fun PhotoStrip(
    photos: Map<Pose, String>,
    canCompare: Boolean,
    photoFile: (String) -> File?,
    onCompare: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        SectionLabel(stringResource(R.string.body_photos))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Pose.entries.forEach { pose ->
                val label = stringResource(pose.labelRes)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.s1),
                ) {
                    PhotoThumb(
                        file = photos[pose]?.let(photoFile),
                        contentDescription = stringResource(R.string.body_photo_description, label),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (pose in photos) label else stringResource(R.string.body_no_photo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onCompare,
            enabled = canCompare,
            modifier = Modifier.fillMaxWidth().heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.body_compare))
        }
    }
}

/** The tab's secondary actions, drawn as Compare is so they read as buttons, not text. */
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = Target.min),
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}
