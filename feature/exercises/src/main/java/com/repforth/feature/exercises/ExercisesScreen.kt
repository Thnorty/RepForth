package com.repforth.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.media.ui.ExerciseMedia
import com.repforth.core.media.ui.ExerciseMediaSize
import com.repforth.core.model.BodyPart
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.BodyView
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.Language
import com.repforth.core.model.Muscle

/**
 * The exercise catalog: search, filters, and 1,324 results.
 *
 * Each row shows the thumbnail along with the exercise name and primary target/equipment
 * chips. Tapping an exercise opens the full detail sheet with animated GIF, muscle breakdown,
 * and step-by-step instructions.
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
        onSelectExercise = viewModel::onSelectExercise,
        onDismissDetail = viewModel::onDismissDetail,
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
    onBodyPartSelected: (BodyPart?) -> Unit,
    onEquipmentSelected: (Equipment?) -> Unit,
    onMuscleToggled: (Muscle) -> Unit,
    onRegionToggled: (BodyRegion) -> Unit,
    onClearFilters: () -> Unit,
    onSelectExercise: (ExerciseSummary) -> Unit,
    onDismissDetail: () -> Unit,
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
        contentPadding = PaddingValues(
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
            ExerciseRow(
                exercise = exercise,
                onClick = { onSelectExercise(exercise) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    state.selectedExercise?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            reducedMotion = state.reducedMotion,
            language = state.language,
            onDismiss = onDismissDetail,
        )
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
                text = pluralStringResource(
                    R.plurals.exercises_results,
                    state.results.size,
                    state.results.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One catalog row with thumbnail, title, and target/equipment chips.
 */
@Composable
private fun ExerciseRow(
    exercise: ExerciseSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
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
}

/**
 * Full detail modal sheet for an exercise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(
    exercise: Exercise,
    reducedMotion: Boolean,
    language: Language?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone)
                .padding(bottom = Space.s6)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 1:1 Flush Media Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                val mediaRef = if (reducedMotion) exercise.thumbnail else exercise.animation
                ExerciseMedia(
                    mediaRef = mediaRef,
                    contentDescription = exercise.name,
                    size = ExerciseMediaSize.FLUSH,
                )
            }

            // Legal attribution notice (§6)
            if (exercise.attribution.isNotBlank()) {
                Text(
                    text = exercise.attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Target & Equipment chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(exercise.target.labelRes)) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(exercise.equipment.labelRes)) },
                )
            }

            if (exercise.secondaryMuscles.isNotEmpty()) {
                val secondaryNames = exercise.secondaryMuscles
                    .map { stringResource(it.labelRes) }
                    .joinToString(", ")
                Text(
                    text = stringResource(R.string.exercises_secondary_muscles, secondaryNames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Step-by-step instructions
            Text(
                text = stringResource(R.string.exercises_instructions),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val activeLang = language ?: Language.ENGLISH
            val steps = exercise.instructions[activeLang].steps
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                steps.forEachIndexed { index, step ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.s2),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.s3),
            ) {
                Text(stringResource(R.string.exercises_close_detail))
            }
        }
    }
}

