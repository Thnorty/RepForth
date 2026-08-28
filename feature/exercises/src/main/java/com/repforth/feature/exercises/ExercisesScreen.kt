package com.repforth.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.model.BodyView
import com.repforth.core.model.ExerciseSummary

/**
 * The exercise catalog: search, filters, and 1,324 results.
 *
 * The list is the product's first screen that reads real data end to end, so it
 * is deliberately plain — a name and the two facts that distinguish one row from
 * the next. Media is absent because the default flavour has none (§6), and an
 * exercise must stay usable from its text alone (§9).
 */
@Composable
fun ExercisesRoute(
    modifier: Modifier = Modifier,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ExercisesScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBodyPartSelected = viewModel::onBodyPartSelected,
        onEquipmentSelected = viewModel::onEquipmentSelected,
        onMuscleToggled = viewModel::onMuscleToggled,
        onRegionToggled = viewModel::onRegionToggled,
        onClearFilters = viewModel::onClearFilters,
        modifier = modifier,
    )
}

/**
 * Stateless, so it can be previewed and tested without Hilt or a database.
 */
@Composable
internal fun ExercisesScreen(
    state: ExercisesUiState,
    onQueryChange: (String) -> Unit,
    onBodyPartSelected: (com.repforth.core.model.BodyPart?) -> Unit,
    onEquipmentSelected: (com.repforth.core.model.Equipment?) -> Unit,
    onMuscleToggled: (com.repforth.core.model.Muscle) -> Unit,
    onRegionToggled: (com.repforth.core.model.BodyRegion) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted above the LazyColumn deliberately. Held inside the filters item,
    // this survived rotation only while that item happened to be on screen:
    // LazyColumn retains saved state for a bounded number of disposed items, so
    // scrolling down far enough evicted it and rotation collapsed the panel.
    var expanded by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf(BodyView.FRONT) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Layout.gutterPhone,
            vertical = Space.s3,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        item(key = "filters") {
            CatalogFilters(
                filter = state.filter,
                expanded = expanded,
                onExpandedChange = { expanded = it },
                view = view,
                onViewChange = { view = it },
                onQueryChange = onQueryChange,
                onBodyPartSelected = onBodyPartSelected,
                onEquipmentSelected = onEquipmentSelected,
                onMuscleToggled = onMuscleToggled,
                onRegionToggled = onRegionToggled,
                onClearFilters = onClearFilters,
            )
        }

        item(key = "summary") {
            ResultSummary(state)
        }

        items(state.results, key = { it.id.value }) { exercise ->
            ExerciseRow(exercise)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ResultSummary(state: ExercisesUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s2),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            state.loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                CircularProgressIndicator(modifier = Modifier.widthIn(max = Space.s5))
                Text(
                    text = stringResource(R.string.exercises_loading),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.isEmptyResult -> Text(
                text = stringResource(R.string.exercises_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            else -> Text(
                text = stringResource(R.string.exercises_results, state.results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One catalog row.
 *
 * The two chips are the target muscle and the equipment, which is what actually
 * separates "barbell bench press" from "dumbbell bench press" in a list of
 * 1,324 — six of which share a name outright.
 */
@Composable
private fun ExerciseRow(exercise: ExerciseSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            // One line, read by TalkBack as one phrase rather than as two
            // orphaned chips.
            text = stringResource(
                R.string.exercises_row_detail,
                stringResource(exercise.target.labelRes),
                stringResource(exercise.equipment.labelRes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
