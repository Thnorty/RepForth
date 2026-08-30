package com.repforth.feature.builder

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import com.repforth.core.media.ui.ExerciseDetailSheet
import com.repforth.core.media.ui.ExerciseMedia
import com.repforth.core.media.ui.ExerciseMediaSize
import com.repforth.core.model.MediaRef

/**
 * The exercise search dialog for adding a movement to the plan.
 *
 * Deliberately thinner than the catalog tab: search and a list, with no body map
 * and no facet rows. Someone here has already decided what they are looking for
 * and is two taps from losing their place in a plan they are part-way through
 * building — browsing belongs on the tab that exists for browsing.
 */
@Composable
internal fun ExercisePicker(
    onPicked: (ExerciseId, String, MediaRef) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    BackHandler(enabled = true, onBack = onClose)

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone, vertical = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.builder_pick_search)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.heightIn(min = Target.min),
            ) {
                Icon(
                    painter = RfIcons.Close,
                    contentDescription = stringResource(R.string.builder_pick_close),
                )
            }
        }

        if (state.results.isEmpty() && !state.loading) {
            Text(
                text = stringResource(R.string.builder_pick_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.s8),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = Layout.gutterPhone,
            ),
        ) {
            items(state.results, key = { it.id.value }) { exercise ->
                PickerRow(exercise = exercise, onClick = { viewModel.onSelectExercise(exercise) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    state.selectedExercise?.let { selected ->
        ExerciseDetailSheet(
            exercise = selected,
            reducedMotion = state.reducedMotion,
            language = state.language,
            targetLabel = stringResource(selected.target.labelRes),
            equipmentLabel = stringResource(selected.equipment.labelRes),
            secondaryMuscleLabels = selected.secondaryMuscles.map { stringResource(it.labelRes) },
            onDismiss = viewModel::onDismissDetail,
            bottomAction = {
                Button(
                    onClick = {
                        onPicked(selected.id, selected.name, selected.thumbnail)
                        viewModel.onDismissDetail()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.builder_add_to_workout))
                }
            },
        )
    }
}

@Composable
private fun PickerRow(exercise: ExerciseSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.min)
            .clickable(onClick = onClick)
            .padding(vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        ExerciseMedia(
            mediaRef = exercise.thumbnail,
            contentDescription = exercise.name,
            size = ExerciseMediaSize.SMALL,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.s1),
        ) {
            Text(text = exercise.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                // Two facts, the same pair the catalog row shows, so an exercise
                // looks the same wherever it is listed.
                text = stringResource(exercise.target.labelRes) + " · " +
                    stringResource(exercise.equipment.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
