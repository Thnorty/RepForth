package com.repforth.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal

/**
 * The first-run questionnaire (§3).
 *
 * Seven questions, one per screen. One-per-screen rather than a single long form
 * because every answer here is a constraint the rules engine will obey for
 * months, and a form invites scrolling past a question rather than answering it.
 *
 * There is no navigation out of this flow. The app shows onboarding while no
 * profile exists, so writing the profile is what ends it — see `AppViewModel`.
 */
@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScreen(
        state = state,
        onGoalSelected = viewModel::onGoalSelected,
        onExperienceSelected = viewModel::onExperienceSelected,
        onEquipmentToggled = viewModel::onEquipmentToggled,
        onDaysChanged = viewModel::onDaysChanged,
        onSessionLengthChanged = viewModel::onSessionLengthChanged,
        onPreferredMuscleToggled = viewModel::onPreferredMuscleToggled,
        onAvoidedMuscleToggled = viewModel::onAvoidedMuscleToggled,
        onBack = viewModel::onBack,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
        onFinish = viewModel::onFinish,
        modifier = modifier,
    )
}

/** Stateless, so the flow can be previewed and tested without Hilt or a database. */
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onGoalSelected: (TrainingGoal) -> Unit,
    onExperienceSelected: (ExperienceLevel) -> Unit,
    onEquipmentToggled: (Equipment) -> Unit,
    onDaysChanged: (Int) -> Unit,
    onSessionLengthChanged: (Int) -> Unit,
    onPreferredMuscleToggled: (Muscle) -> Unit,
    onAvoidedMuscleToggled: (Muscle) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = Layout.contentMaxPhone)
            .padding(horizontal = Layout.gutterPhone),
    ) {
        StepHeader(state)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // The content scrolls, the header and footer do not. At 200%
                // font scale a set of options does not fit, and the way out
                // must not be what scrolls off.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            when (state.step) {
                OnboardingStep.GOAL -> SingleChoice(
                    options = TrainingGoal.entries,
                    selected = state.goal,
                    labelOf = { stringResource(it.labelRes) },
                    onSelected = onGoalSelected,
                )

                OnboardingStep.EXPERIENCE -> SingleChoice(
                    options = ExperienceLevel.entries,
                    selected = state.experience,
                    labelOf = { stringResource(it.labelRes) },
                    onSelected = onExperienceSelected,
                )

                OnboardingStep.EQUIPMENT -> {
                    ChipChoice(
                        options = Equipment.entries,
                        isSelected = { it in state.equipment },
                        labelOf = { stringResource(it.labelRes) },
                        onToggled = onEquipmentToggled,
                    )
                    Hint(stringResource(R.string.onboarding_equipment_none))
                }

                OnboardingStep.DAYS -> ValueSlider(
                    value = state.trainingDaysPerWeek,
                    range = OnboardingUiState.DAYS_RANGE,
                    label = stringResource(
                        R.string.onboarding_days_value,
                        state.trainingDaysPerWeek,
                    ),
                    onValueChange = onDaysChanged,
                )

                OnboardingStep.LENGTH -> ValueSlider(
                    value = state.sessionLengthMinutes,
                    range = OnboardingUiState.SESSION_MINUTES_RANGE,
                    step = SESSION_STEP_MINUTES,
                    label = stringResource(
                        R.string.onboarding_length_value,
                        state.sessionLengthMinutes,
                    ),
                    onValueChange = onSessionLengthChanged,
                )

                OnboardingStep.MUSCLES -> ChipChoice(
                    options = canonicalMuscles,
                    isSelected = { it in state.preferredMuscles },
                    labelOf = { stringResource(it.labelRes) },
                    onToggled = onPreferredMuscleToggled,
                )

                OnboardingStep.AVOID -> {
                    ChipChoice(
                        options = canonicalMuscles,
                        isSelected = { it in state.avoidedMuscles },
                        labelOf = { stringResource(it.labelRes) },
                        onToggled = onAvoidedMuscleToggled,
                    )
                    Hint(stringResource(R.string.onboarding_privacy))
                }
            }
        }

        StepFooter(
            state = state,
            onBack = onBack,
            onNext = onNext,
            onSkip = onSkip,
            onFinish = onFinish,
        )
    }
}

@Composable
private fun StepHeader(state: OnboardingUiState) {
    Column(
        modifier = Modifier.padding(top = Space.s6, bottom = Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        LinearProgressIndicator(
            progress = { state.stepNumber.toFloat() / state.stepCount },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.onboarding_step,
                state.stepNumber,
                state.stepCount,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(state.step.titleRes),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(state.step.subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepFooter(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s4),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isFirstStep) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.onboarding_back))
            }
        }

        // Skip sits beside Back rather than beside the primary action, so the
        // button under the thumb is never the one that discards the question.
        if (state.step.optional && !state.isLastStep) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        Button(
            onClick = if (state.isLastStep) onFinish else onNext,
            enabled = state.canAdvance && !state.saving,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Target.min),
        ) {
            Text(
                stringResource(
                    if (state.isLastStep) R.string.onboarding_finish else R.string.onboarding_next,
                ),
            )
        }
    }
}

/** One answer, chosen from a short list. Radio semantics, card-sized targets. */
@Composable
private fun <T> SingleChoice(
    options: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Target.session)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    ),
                colors = if (isSelected) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Text(
                    text = labelOf(option),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(Space.s4),
                )
            }
        }
    }
}

/** Any number of answers, from a long list. Wraps rather than scrolling sideways. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipChoice(
    options: List<T>,
    isSelected: (T) -> Boolean,
    labelOf: @Composable (T) -> String,
    onToggled: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = isSelected(option),
                onClick = { onToggled(option) },
                label = { Text(labelOf(option)) },
            )
        }
    }
}

/**
 * A whole number on a slider, with the value written above it.
 *
 * The number is the answer, so it is stated in words rather than left to be read
 * off the track position.
 */
@Composable
private fun ValueSlider(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // One less than the number of stops: Slider counts the gaps between
            // them, not the stops themselves.
            steps = ((range.last - range.first) / step) - 1,
            modifier = Modifier.heightIn(min = Target.min),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.s2),
    )
}

/**
 * One chip per muscle, not one per upstream name.
 *
 * `abs` and `abdominals` are the same muscle under two labels; showing both
 * would ask the same question twice and let it be answered two ways.
 */
private val canonicalMuscles: List<Muscle> =
    Muscle.entries.filter { it.canonical == it }.sortedBy { it.slug }

private const val SESSION_STEP_MINUTES = 5
