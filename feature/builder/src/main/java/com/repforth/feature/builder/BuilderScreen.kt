package com.repforth.feature.builder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.designsystem.theme.formatWeight
import com.repforth.core.designsystem.theme.symbol
import com.repforth.core.designsystem.theme.toKilograms
import com.repforth.core.media.ui.ExerciseMedia
import com.repforth.core.media.ui.ExerciseMediaSize
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.Language

/**
 * The manual workout builder (§3, §12).
 *
 * A list of editable cards, which §12 requires: the same screen builds a plan by
 * hand and edits one that already exists, so a generated plan later arrives here
 * rather than needing its own editor.
 */
@Composable
fun BuilderRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    planId: String? = null,
    onExit: () -> Unit = onSaved,
    viewModel: BuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val defaultCoachName = stringResource(R.string.coach_default_name)
    val language = Language.fromTag(LocalConfiguration.current.locales[0].language)
        ?: Language.ENGLISH

    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(planId) {
        if (planId != null) viewModel.load(planId)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    val isDirty = state.exercises.isNotEmpty() || state.name.isNotBlank()
    if (!state.picking && !state.coaching) {
        BackHandler(enabled = !confirmingDiscard) {
            if (isDirty) {
                confirmingDiscard = true
            } else {
                onExit()
            }
        }
    }

    when {
        state.picking -> ExercisePicker(
            onPicked = { id, name, thumb -> viewModel.onExerciseAdded(id, name, thumb) },
            onClose = viewModel::onPickerClose,
            modifier = modifier,
        )

        state.coaching -> CoachScreen(
            state = state,
            onMuscleToggled = viewModel::onCoachMuscleToggled,
            onRegionToggled = viewModel::onCoachRegionToggled,
            onGenerate = { name -> viewModel.onGenerate(name, language) },
            onCancelGenerate = viewModel::onCancelGenerate,
            onDismissError = viewModel::onDismissCoachError,
            onClose = viewModel::onCoachClose,
            modifier = modifier,
        )

        else -> BuilderScreen(
            state = state,
            onNameChange = viewModel::onNameChange,
            onAddExercise = viewModel::onPickerOpen,
            onCoach = viewModel::onCoachOpen,
            onRemove = viewModel::onRemove,
            onMove = viewModel::onMove,
            onSetsChange = viewModel::onSetsChange,
            onRepsChange = viewModel::onRepsChange,
            onDurationChange = viewModel::onDurationChange,
            onRestChange = viewModel::onRestChange,
            onWeightChange = viewModel::onWeightChange,
            onTimedChange = viewModel::onTimedChange,
            onSave = viewModel::onSave,
            modifier = modifier,
        )
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.builder_discard_dialog_title)) },
            text = { Text(stringResource(R.string.builder_discard_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDiscard = false
                    onExit()
                }) {
                    Text(stringResource(R.string.builder_discard_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) {
                    Text(stringResource(R.string.builder_discard_dialog_dismiss))
                }
            },
        )
    }
}

/** Stateless, so it can be previewed and tested without Hilt or a database. */
@Composable
internal fun BuilderScreen(
    state: BuilderUiState,
    onNameChange: (String) -> Unit,
    onAddExercise: () -> Unit,
    onCoach: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSetsChange: (Int, Int) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onDurationChange: (Int, Int) -> Unit,
    onRestChange: (Int, Int) -> Unit,
    onWeightChange: (Int, Double?) -> Unit,
    onTimedChange: (Int, Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = Layout.gutterPhone,
                vertical = Space.s3,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.builder_name_label)) },
                    placeholder = { Text(stringResource(R.string.builder_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.coachNotice?.let { notice ->
                item(key = "coach_notice") {
                    CoachNoticeCard(notice = notice)
                }
            }

            if (state.exercises.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.builder_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.s8),
                    )
                }
            }

            itemsIndexed(state.exercises, key = { _, e -> e.id }) { index, draft ->
                ExerciseCard(
                    draft = draft,
                    index = index,
                    total = state.exercises.size,
                    onRemove = { onRemove(index) },
                    onMove = { to -> onMove(index, to) },
                    onSetsChange = { onSetsChange(index, it) },
                    onRepsChange = { onRepsChange(index, it) },
                    onDurationChange = { onDurationChange(index, it) },
                    onRestChange = { onRestChange(index, it) },
                    onWeightChange = { onWeightChange(index, it) },
                    onTimedChange = { onTimedChange(index, it) },
                )
            }

            item(key = "add") {
                OutlinedButton(
                    onClick = onAddExercise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.builder_add_exercise))
                }
            }

            item(key = "coach") {
                OutlinedButton(
                    onClick = onCoach,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.coach_open))
                }
            }
        }

        BuilderFooter(state = state, onSave = onSave)
    }
}

@Composable
private fun CoachNoticeCard(
    notice: CoachNotice,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Text(
                text = stringResource(
                    R.string.coach_provider_rationale,
                    notice.rationale,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The estimate and the way out, pinned below the list.
 *
 * Pinned rather than scrolled because §12 requires the primary action to survive
 * 200% font scaling without being clipped, and a Save at the bottom of a long
 * list of cards is exactly what disappears at that size.
 */
@Composable
private fun BuilderFooter(state: BuilderUiState, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        if (state.exercises.isNotEmpty()) {
            Text(
                text = stringResource(R.string.builder_estimate, state.estimatedMinutes),
                style = MaterialTheme.typography.labelLarge,
            )
            // Stated, not enforced. The session length from onboarding is what
            // the user said a normal session is, not a rule they asked to be
            // held to, and refusing to save their own plan would be the app
            // overruling them about their own time.
            if (state.exceedsCeiling && state.sessionCeilingMinutes != null) {
                Text(
                    text = stringResource(
                        R.string.builder_over_ceiling,
                        state.sessionCeilingMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (state.name.isBlank() && state.exercises.isNotEmpty()) {
            Text(
                text = stringResource(R.string.builder_name_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.builder_save))
        }
    }
}

@Composable
private fun ExerciseCard(
    draft: DraftExercise,
    index: Int,
    total: Int,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    onWeightChange: (Double?) -> Unit,
    onTimedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                ExerciseMedia(
                    mediaRef = draft.thumbnail,
                    contentDescription = draft.name,
                    size = ExerciseMediaSize.SMALL,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.builder_position, index + 1, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = { onMove(index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.size(Target.min),
                ) {
                    Icon(
                        painter = RfIcons.MoveUp,
                        contentDescription =
                            stringResource(R.string.builder_move_up, draft.name),
                        modifier = Modifier.size(Space.s5),
                    )
                }
                IconButton(
                    onClick = { onMove(index + 1) },
                    enabled = index < total - 1,
                    modifier = Modifier.size(Target.min),
                ) {
                    Icon(
                        painter = RfIcons.MoveDown,
                        contentDescription =
                            stringResource(R.string.builder_move_down, draft.name),
                        modifier = Modifier.size(Space.s5),
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(Target.min)) {
                    Icon(
                        painter = RfIcons.Delete,
                        contentDescription =
                            stringResource(R.string.builder_remove, draft.name),
                        modifier = Modifier.size(Space.s5),
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !draft.timed,
                    onClick = { onTimedChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(stringResource(R.string.builder_mode_reps))
                }
                SegmentedButton(
                    selected = draft.timed,
                    onClick = { onTimedChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text(stringResource(R.string.builder_mode_duration))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                NumberField(
                    value = draft.sets,
                    label = stringResource(R.string.builder_sets),
                    onValueChange = onSetsChange,
                    modifier = Modifier.weight(1f),
                )
                if (draft.timed) {
                    NumberField(
                        value = draft.durationSeconds,
                        label = stringResource(R.string.builder_seconds),
                        onValueChange = onDurationChange,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    NumberField(
                        value = draft.reps,
                        label = stringResource(R.string.builder_reps),
                        onValueChange = onRepsChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                DecimalField(
                    value = draft.weightKg,
                    label = stringResource(R.string.builder_weight, LocalUnitSystem.current.symbol),
                    onValueChange = onWeightChange,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = draft.restSeconds,
                    label = stringResource(R.string.builder_rest),
                    onValueChange = onRestChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A whole number, typed.
 *
 * Holds its own [TextFieldValue] rather than rendering the model's number
 * directly. Passing a `String` down looks simpler and is wrong: clearing a field
 * containing "2" produces text that does not parse, the change is dropped, the
 * model still says 2, and the field is recomposed back to "2" — with the cursor
 * reset to the front. Typing "1" then gives "12". Owning the text means the
 * field can be empty for as long as it takes to retype it, and the cursor stays
 * where the user put it.
 *
 * The model is still the authority: when it changes underneath — a clamp, or a
 * plan being loaded — the text is resynchronised, but only when it actually
 * disagrees, so an in-progress edit is never yanked out from under the caret.
 */
@Composable
private fun NumberField(
    value: Int,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(TextFieldValue(value.toString())) }

    LaunchedEffect(value) {
        if (text.text.toIntOrNull() != value) {
            text = TextFieldValue(value.toString(), TextRange(value.toString().length))
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.text.filter(Char::isDigit).take(MAX_DIGITS)
            text = typed.copy(text = digits)
            // An empty field is a state to pass through, not a value to store.
            digits.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** The same ownership rule as [NumberField]; blank clears the weight. */
/**
 * A weight, shown and typed in the user's unit but stored in kilograms.
 *
 * §7 keeps kilograms as the stored unit and converts only for display, so the
 * conversion happens on the way in and out of this field and nowhere else.
 */
@Composable
private fun DecimalField(
    value: Double?,
    label: String,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = LocalUnitSystem.current
    fun shown(kg: Double?) = kg?.let { units.formatWeight(it) } ?: ""

    var text by remember { mutableStateOf(TextFieldValue(shown(value))) }

    LaunchedEffect(value, units) {
        if (text.text != shown(value)) {
            text = TextFieldValue(shown(value), TextRange(shown(value).length))
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val cleaned = typed.text.filter { it.isDigit() || it == '.' }.take(MAX_DIGITS)
            text = typed.copy(text = cleaned)
            onValueChange(
                if (cleaned.isBlank()) null else cleaned.toDoubleOrNull()?.let(units::toKilograms),
            )
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private const val MAX_DIGITS = 6
