package com.repforth.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.repforth.core.designsystem.R
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.BodyView
import com.repforth.core.model.Muscle
import com.repforth.core.model.region

/**
 * Choosing muscles by pointing at a body, with chips for what cannot be pointed at.
 *
 * Two screens ask the same question — which muscles does this concern? — and the
 * catalog's answer was the one that worked on a device, so onboarding uses it
 * rather than a second wall of forty chips. Extracted here on the second use
 * rather than copied, which also means the fixes already learned from the
 * catalog apply to both: selections surface as their own row, and synonyms
 * collapse to one chip.
 *
 * [labelOf] is a parameter because muscle names belong to the catalog vocabulary
 * in `core:exercise-data`, and the design system must not depend on it. The
 * caller supplies the words; this owns the arrangement.
 *
 * [view] is hoisted rather than remembered internally. The catalog puts this
 * inside a `LazyColumn`, which evicts saved state for items scrolled far enough
 * out of view — held here, the front/back choice silently reset on rotation.
 */
@Composable
fun MuscleSelector(
    selected: Set<Muscle>,
    view: BodyView,
    onViewChange: (BodyView) -> Unit,
    onMuscleToggled: (Muscle) -> Unit,
    onRegionToggled: (BodyRegion) -> Unit,
    labelOf: @Composable (Muscle) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.45f),
        ) {
            BodyView.entries.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = view == candidate,
                    onClick = { onViewChange(candidate) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, BodyView.entries.size),
                ) {
                    Text(
                        stringResource(
                            if (candidate == BodyView.FRONT) {
                                R.string.rf_body_view_front
                            } else {
                                R.string.rf_body_view_back
                            },
                        ),
                    )
                }
            }
        }

        // The map does not look tappable. It was reported as unclear on a
        // device, and a silhouette gives no affordance the way a chip does, so
        // the invitation is written rather than implied.
        Text(
            text = stringResource(R.string.rf_body_map_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val mapDescription = stringResource(R.string.rf_body_map)
        BodyMap(
            view = view,
            selected = selected.mapNotNullTo(mutableSetOf()) { it.region },
            onRegionClick = onRegionToggled,
            enabled = enabled,
            modifier = Modifier
                .height(Layout.bodyMapHeight)
                // One description for the whole map. Announcing each path would
                // bury the chips below, which are what actually works with a
                // screen reader.
                .semantics { contentDescription = mapDescription },
        )

        // What is selected, shown separately and first.
        //
        // Tapping a region selects several muscles at once, and they were
        // scattered through a scrolling row of 41 chips — so the map appeared to
        // do nothing. Selected muscles surface here, in canonical form so
        // `pectorals` and `chest` are one chip rather than two names for one
        // thing.
        val chosen = selected.map { it.canonical }.distinct().sortedBy { it.slug }
        if (chosen.isNotEmpty()) {
            SelectorLabel(R.string.rf_selected_muscles)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                items(chosen, key = { it.name }) { muscle ->
                    val label = labelOf(muscle)
                    InputChip(
                        selected = true,
                        onClick = { onMuscleToggled(muscle) },
                        enabled = enabled,
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                painter = RfIcons.Close,
                                contentDescription =
                                    stringResource(R.string.rf_remove_muscle, label),
                            )
                        },
                    )
                }
            }
        }

        // Everything not yet chosen, including the muscles no silhouette can
        // show. Canonical only, so synonyms do not appear as separate chips.
        val available = Muscle.entries.filter { it.canonical == it && it !in selected }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            items(available, key = { it.name }) { muscle ->
                FilterChip(
                    selected = false,
                    onClick = { onMuscleToggled(muscle) },
                    enabled = enabled,
                    label = { Text(labelOf(muscle)) },
                )
            }
        }
    }
}

@Composable
private fun SelectorLabel(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
