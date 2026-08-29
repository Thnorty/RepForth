package com.repforth.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.repforth.core.designsystem.component.MuscleSelector
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.BodyPart
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.BodyView
import com.repforth.core.model.Equipment
import com.repforth.core.model.Muscle
import com.repforth.core.model.region

/**
 * Search plus the three MVP facets (§3).
 *
 * The body map and the muscle chips are two ways into the same filter, not
 * alternatives: §12 forbids relying on colour alone, and `cardiovascular
 * system` is not a place on a body, so the chips are what makes every muscle
 * reachable. Tapping a region selects every muscle in it, which is why a region
 * is a lossy view onto the vocabulary rather than a replacement for it.
 */
@Composable
internal fun CatalogFilters(
    filter: CatalogFilter,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    view: BodyView,
    onViewChange: (BodyView) -> Unit,
    onQueryChange: (String) -> Unit,
    onBodyPartSelected: (BodyPart?) -> Unit,
    onEquipmentSelected: (Equipment?) -> Unit,
    onMuscleToggled: (Muscle) -> Unit,
    onRegionToggled: (BodyRegion) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.exercises_search_hint)) },
            supportingText = { Text(stringResource(R.string.exercises_names_english)) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painter = RfIcons.Close,
                            contentDescription = stringResource(R.string.exercises_clear_query),
                        )
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onExpandedChange(!expanded) }) {
                Text(stringResource(R.string.exercises_filters))
            }
            if (!filter.isEmpty) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.exercises_clear_filters))
                }
            }
        }

        if (expanded) {
            FacetLabel(R.string.exercises_body_part)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                items(BodyPart.entries, key = { it.name }) { bodyPart ->
                    FilterChip(
                        selected = filter.bodyPart == bodyPart,
                        onClick = { onBodyPartSelected(bodyPart) },
                        label = { Text(stringResource(bodyPart.labelRes)) },
                    )
                }
            }

            FacetLabel(R.string.exercises_equipment)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                items(Equipment.entries, key = { it.name }) { equipment ->
                    FilterChip(
                        selected = filter.equipment == equipment,
                        onClick = { onEquipmentSelected(equipment) },
                        label = { Text(stringResource(equipment.labelRes)) },
                    )
                }
            }

            FacetLabel(R.string.exercises_muscles)
            MuscleSelector(
                selected = filter.muscles,
                view = view,
                onViewChange = onViewChange,
                onMuscleToggled = onMuscleToggled,
                onRegionToggled = onRegionToggled,
                labelOf = { stringResource(it.labelRes) },
            )
        }
    }
}

@Composable
private fun FacetLabel(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.s2),
    )
}

/**
 * A region reads as selected when any muscle in it is, so the map reflects a
 * chip tap even for a muscle the user picked by name rather than by pointing.
 */
private fun selectedRegions(muscles: Set<Muscle>): Set<BodyRegion> =
    muscles.mapNotNullTo(mutableSetOf()) { it.region }
