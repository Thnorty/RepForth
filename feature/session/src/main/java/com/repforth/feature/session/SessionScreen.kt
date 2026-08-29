package com.repforth.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.model.ExerciseTarget

/**
 * The running workout (§3, §10).
 *
 * Everything here is sized for someone out of breath with chalk on their hands:
 * controls are [Target.session] rather than the 48dp floor, and the number that
 * matters is the largest thing on screen. §12 calls that numeric hierarchy, and
 * this is the screen it was written for.
 */
@Composable
fun SessionRoute(
    templateId: String?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(templateId, state.loading) {
        if (templateId != null && !state.loading && state.snapshot == null) {
            viewModel.start(templateId)
        }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) onExit()
    }

    // The countdown's heartbeat. Keyed on whether we are resting, so it exists
    // only while there is something to count and stops when the screen leaves.
    LaunchedEffect(state.isResting) {
        while (state.isResting) {
            viewModel.onTick()
            delay(SessionViewModel.TICK_MS)
        }
    }

    SessionScreen(
        state = state,
        onCompleteSet = viewModel::onCompleteSet,
        onSkipSet = viewModel::onSkipSet,
        onSkipRest = viewModel::onSkipRest,
        onNextExercise = viewModel::onNextExercise,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onFinish = viewModel::onFinish,
        onAbandon = viewModel::onAbandon,
        modifier = modifier,
    )
}

@Composable
internal fun SessionScreen(
    state: SessionUiState,
    onCompleteSet: (Int?, Double?, Long?) -> Unit,
    onSkipSet: () -> Unit,
    onSkipRest: () -> Unit,
    onNextExercise: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    if (snapshot == null) {
        EmptyMessage(text = stringResource(R.string.session_none), modifier = modifier)
        return
    }

    var confirmingAbandon by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Layout.gutterPhone),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        SessionHeader(state)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.isResting -> RestPanel(state)
                state.isPaused -> Headline(stringResource(R.string.session_paused))
                state.isCompleting -> Headline(stringResource(R.string.session_completing))
                else -> TargetPanel(state)
            }
        }

        SessionControls(
            state = state,
            onCompleteSet = onCompleteSet,
            onSkipSet = onSkipSet,
            onSkipRest = onSkipRest,
            onNextExercise = onNextExercise,
            onPause = onPause,
            onResume = onResume,
            onFinish = onFinish,
            onAbandon = { confirmingAbandon = true },
        )
    }

    if (confirmingAbandon) {
        AlertDialog(
            onDismissRequest = { confirmingAbandon = false },
            title = { Text(stringResource(R.string.session_confirm_abandon_title)) },
            // Says what is kept, not what is lost. Abandoning does not discard
            // the sets already performed, and a dialog that implies otherwise
            // makes people finish workouts they wanted to stop.
            text = { Text(stringResource(R.string.session_confirm_abandon_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingAbandon = false
                    onAbandon()
                }) {
                    Text(stringResource(R.string.session_confirm_abandon_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingAbandon = false }) {
                    Text(stringResource(R.string.session_confirm_abandon_no))
                }
            },
        )
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxSize().padding(Layout.gutterPhone),
    )
}

@Composable
private fun SessionHeader(state: SessionUiState) {
    Column(
        modifier = Modifier.padding(top = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        LinearProgressIndicator(
            progress = {
                if (state.exerciseTotal == 0) 0f
                else state.exerciseNumber.toFloat() / state.exerciseTotal
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.session_exercise_of,
                state.exerciseNumber,
                state.exerciseTotal,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.currentName.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.session_set_of, state.setNumber, state.setTotal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The rest countdown. The number is the screen. */
@Composable
private fun RestPanel(state: SessionUiState) {
    val seconds = ((state.restRemainingMs ?: 0L) / 1000L).toInt()
    val label = stringResource(R.string.session_resting)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = seconds.toString(),
            style = RepForthNumeric.xl,
            // Announced as it changes, but not every second: §12 forbids
            // narrating each tick. Polite means the reader finishes what it is
            // saying first, which in practice collapses a run of ticks into one.
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "$label $seconds"
            },
        )
    }
}

@Composable
private fun TargetPanel(state: SessionUiState) {
    val target = state.target ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when (target) {
                is ExerciseTarget.Reps -> target.reps.toString()
                is ExerciseTarget.Duration -> (target.durationMs / 1000L).toString()
            },
            style = RepForthNumeric.xl,
        )
        Text(
            text = when (target) {
                is ExerciseTarget.Reps -> stringResource(R.string.session_reps)
                is ExerciseTarget.Duration -> stringResource(R.string.session_seconds)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        target.weightKg?.let { weight ->
            Text(
                text = stringResource(
                    R.string.session_target_weight,
                    if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString(),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SessionControls(
    state: SessionUiState,
    onCompleteSet: (Int?, Double?, Long?) -> Unit,
    onSkipSet: () -> Unit,
    onSkipRest: () -> Unit,
    onNextExercise: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onAbandon: () -> Unit,
) {
    var reps by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        if (state.isActive && state.target is ExerciseTarget.Reps) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    label = { Text(stringResource(R.string.session_reps)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = {
                        weight = it.filter { c -> c.isDigit() || c == '.' }.take(MAX_DIGITS)
                    },
                    label = { Text(stringResource(R.string.session_weight)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when {
            state.isResting -> PrimaryAction(
                text = stringResource(R.string.session_skip_rest),
                onClick = onSkipRest,
            )

            state.isPaused -> PrimaryAction(
                text = stringResource(R.string.session_resume),
                onClick = onResume,
            )

            state.isCompleting -> PrimaryAction(
                text = stringResource(R.string.session_finish),
                onClick = onFinish,
            )

            else -> PrimaryAction(
                text = stringResource(R.string.session_log_set),
                onClick = {
                    // Blank means "as prescribed": the target is what was
                    // planned, and typing it again to confirm it is friction
                    // during the one activity where typing is hardest.
                    onCompleteSet(
                        reps.toIntOrNull(),
                        weight.toDoubleOrNull(),
                        (state.target as? ExerciseTarget.Duration)?.durationMs,
                    )
                    reps = ""
                    weight = ""
                },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            if (!state.isPaused && !state.isCompleting) {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f).heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.session_pause))
                }
            }
            if (state.isActive) {
                OutlinedButton(
                    onClick = onSkipSet,
                    modifier = Modifier.weight(1f).heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.session_skip_set))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            if (state.isActive || state.isResting) {
                TextButton(
                    onClick = onNextExercise,
                    modifier = Modifier.weight(1f).heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.session_next_exercise))
                }
            }
            TextButton(
                onClick = onAbandon,
                modifier = Modifier.weight(1f).heightIn(min = Target.min),
            ) {
                Text(stringResource(R.string.session_abandon))
            }
        }
    }
}

/** The one control used mid-set, at the session touch target rather than the floor. */
@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Target.session),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private const val MAX_DIGITS = 5
