package com.repforth.feature.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.component.RfProgressRing
import com.repforth.core.designsystem.component.RingTone
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.LocalUnitSystem
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.designsystem.theme.formatWeight
import com.repforth.core.designsystem.theme.rfPopOnChange
import com.repforth.core.designsystem.theme.symbol
import com.repforth.core.designsystem.theme.toKilograms
import com.repforth.core.media.ui.ExerciseMedia
import com.repforth.core.media.ui.ExerciseMediaSize
import com.repforth.core.model.ExerciseTarget
import kotlinx.coroutines.delay

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

    // Always tell the view model which plan was tapped. Deciding what that
    // means is its job, not this effect's.
    //
    // **This used to be guarded by `state.snapshot == null`, and that guard was
    // the whole bug.** The view model restores the running session as it is
    // created, so by the time this effect ran there was already a snapshot —
    // and `start` was therefore never called at all. Every outcome it can
    // return, including the conflict this screen exists to raise, was
    // unreachable: the screen simply displayed whatever had been restored,
    // which is precisely the "tapping a plan resumes a different one" this was
    // supposed to have fixed.
    //
    // Keyed on `templateId` alone so it fires once per plan rather than every
    // time loading flips. `start` is safe to repeat: asking for the plan that
    // is already running is `Resumed`, not a conflict.
    LaunchedEffect(templateId) {
        if (templateId != null) {
            viewModel.start(templateId)
        }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) onExit()
    }

    // The countdown's heartbeat while the screen is up. The service keeps its
    // own, coarser one for when it is not.
    LaunchedEffect(state.isResting) {
        while (state.isResting) {
            viewModel.onTick()
            delay(REST_TICK_MS)
        }
    }

    val context = LocalContext.current

    // Onboarding asks for this, with a reason beside the button. This is the
    // net for profiles created before that step existed: launching when the
    // permission is already granted returns immediately and shows nothing, and
    // after a refusal the system does not ask again — so this cannot become a
    // prompt anyone sees twice.
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // §10: the service lives exactly as long as the workout does.
    LaunchedEffect(state.snapshot?.phase) {
        val phase = state.snapshot?.phase
        if (phase != null && !phase.isTerminal) {
            WorkoutService.start(context)
        } else {
            WorkoutService.stop(context)
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
        onKeepRunningSession = viewModel::onKeepRunningSession,
        onDiscardRunningAndStart = viewModel::onDiscardRunningAndStart,
        modifier = modifier,
    )
}

@Composable
internal fun SessionScreen(
    state: SessionUiState,
    onCompleteSet: (Int?, Double?, Long?) -> Unit,
    onKeepRunningSession: () -> Unit,
    onDiscardRunningAndStart: () -> Unit,
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

    // A different workout was already running when this plan was started. §10
    // will not discard it and will not silently swap to it, so the screen asks.
    //
    // Raised above the null check below, not after it. The conflict is known as
    // soon as `start` answers, which can be before the running session's
    // snapshot has been adopted — and a dialog rendered after an early return
    // is a dialog that never appears in exactly that window.
    state.conflictingSession?.let {
        WorkoutConflictDialog(
            runningName = state.conflictingName,
            onKeep = onKeepRunningSession,
            onDiscard = onDiscardRunningAndStart,
            // Here there is no list to fall back to -- this screen is already
            // showing the running workout. Dismissing therefore means the same
            // thing as keeping it, which is what is behind the dialog anyway.
            onCancel = onKeepRunningSession,
        )
    }

    if (snapshot == null) {
        EmptyMessage(text = stringResource(R.string.session_none), modifier = modifier)
        return
    }

    var confirmingAbandon by rememberSaveable { mutableStateOf(false) }

    // Back asks the same question the button does.
    //
    // Without this, back left the workout running and returned to Plans, where
    // Start resumed it — which reads as the workout having been abandoned and
    // then quietly coming back. Starting a *different* plan is now a question
    // rather than a silent swap, but this is still the only way out, and it
    // asks first.
    BackHandler(enabled = !confirmingAbandon) { confirmingAbandon = true }

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
        // "Spring for set completion" -- the design system's second motion
        // rule, and the only motion permitted on this screen while a set is in
        // progress. It pops when the set number changes and at no other time.
        val setPop = rfPopOnChange(state.setNumber)
        Text(
            text = stringResource(R.string.session_set_of, state.setNumber, state.setTotal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // This number changes with no one touching the screen: when the
            // rest timer runs out, `RestElapsed` advances the set and the
            // counter moves on its own. Polite, not assertive -- it should be
            // read at the next pause rather than cutting off whatever the user
            // is already listening to.
            modifier = Modifier
                .graphicsLayer {
                    scaleX = setPop
                    scaleY = setPop
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The rest countdown. The number is the screen. */
@Composable
private fun RestPanel(state: SessionUiState) {
    val seconds = ((state.restRemainingMs ?: 0L) / 1000L).toInt()
    val label = stringResource(R.string.session_resting)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        val countdown = @Composable {
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

        // The ring only appears when there is a rest length to measure against.
        // Without one it would be a full circle that never moves, which says
        // less than the number alone and costs a lot more room.
        val fraction = state.restFraction
        if (fraction != null) {
            RfProgressRing(
                progress = fraction,
                tone = RingTone.Rest,
                // The ring is told how often this value changes so it can sweep
                // across the gap instead of easing to a halt inside it.
                stepMillis = REST_TICK_MS.toInt(),
                content = countdown,
            )
        } else {
            countdown()
        }

        state.nextUpPreview?.let { next ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                ExerciseMedia(
                    mediaRef = next.thumbnail,
                    contentDescription = next.name,
                    size = ExerciseMediaSize.SMALL,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.session_next_up, next.name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (next.nextSetNumber != null && next.totalSets != null) {
                        Text(
                            text = stringResource(R.string.session_set_of, next.nextSetNumber, next.totalSets),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetPanel(state: SessionUiState) {
    val target = state.target ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        val mediaRef = state.currentExercise?.let {
            if (state.reducedMotion) it.thumbnail else it.animation
        }
        if (mediaRef != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1.1f),
                contentAlignment = Alignment.Center,
            ) {
                ExerciseMedia(
                    mediaRef = mediaRef,
                    contentDescription = state.currentName,
                    size = ExerciseMediaSize.FLUSH,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val units = LocalUnitSystem.current
            Text(
                text = when (target) {
                    is ExerciseTarget.Reps -> target.reps.toString()
                    is ExerciseTarget.Duration -> (target.durationMs / 1000L).toString()
                },
                style = RepForthNumeric.xl,
            )
            Text(
                // The unit of the big number above, and nothing else. This line
                // also appended "· 60 kg" while the line directly below it said
                // "60 kg" in the accent colour — the same value, from the same
                // field, twice. Seen in the first screenshot ever taken of this
                // screen; the accent line is the one that stays, because it is
                // the prominent one and it is what says "Bodyweight" when there
                // is no load.
                text = when (target) {
                    is ExerciseTarget.Reps -> stringResource(R.string.session_reps)
                    is ExerciseTarget.Duration -> stringResource(R.string.session_seconds)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            target.weightKg?.takeIf { it > 0.0 }?.let { weight ->
                Text(
                    text = stringResource(
                        R.string.session_target_weight,
                        units.formatWeight(weight),
                        units.symbol,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: run {
                Text(
                    text = stringResource(R.string.session_target_bodyweight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    val units = LocalUnitSystem.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        if (state.isActive && state.target is ExerciseTarget.Reps) {
            val targetReps = (state.target as? ExerciseTarget.Reps)?.reps
            val targetWeight = state.target?.weightKg
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(MAX_DIGITS) },
                    label = { Text(stringResource(R.string.session_reps)) },
                    placeholder = {
                        targetReps?.let { Text(it.toString()) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = {
                        weight = it.filter { c -> c.isDigit() || c == '.' }.take(MAX_DIGITS)
                    },
                    label = {
                        Text(stringResource(R.string.session_weight, units.symbol))
                    },
                    placeholder = {
                        targetWeight?.takeIf { it > 0.0 }?.let {
                            Text(units.formatWeight(it))
                        }
                    },
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
                        // Typed in the user's unit, stored in kilograms (§7).
                        weight.toDoubleOrNull()?.let(units::toKilograms),
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
            // Not offered once every set is done. §10 allows abandoning from
            // COMPLETING, but the only thing left to do there is finish, and a
            // second way out beside it just asks the user to decide between two
            // words for the same moment.
            if (!state.isCompleting) {
                TextButton(
                    onClick = onAbandon,
                    modifier = Modifier.weight(1f).heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.session_abandon))
                }
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
