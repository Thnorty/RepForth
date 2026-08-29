package com.repforth.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import com.repforth.core.exercisedata.detailRes
import androidx.compose.runtime.getValue
import com.repforth.core.model.BodyView
import com.repforth.core.model.BodyRegion
import com.repforth.core.designsystem.component.MuscleSelector
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Radius
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Stroke
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.exercisedata.labelRes
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import kotlin.math.roundToInt

/**
 * The first-run questionnaire (Â§3).
 *
 * Seven questions, one per screen. One-per-screen rather than a single long form
 * because every answer here is a constraint the rules engine will obey for
 * months, and a form invites scrolling past a question rather than answering it.
 *
 * There is no navigation out of this flow. The app shows onboarding while no
 * profile exists, so writing the profile is what ends it â€” see `AppViewModel`.
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
        onPreferredRegionToggled = viewModel::onPreferredRegionToggled,
        onAvoidedMuscleToggled = viewModel::onAvoidedMuscleToggled,
        onAvoidedRegionToggled = viewModel::onAvoidedRegionToggled,
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
    onPreferredRegionToggled: (BodyRegion) -> Unit,
    onAvoidedMuscleToggled: (Muscle) -> Unit,
    onAvoidedRegionToggled: (BodyRegion) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shared by both muscle steps on purpose: turning the body round to pick a
    // muscle to focus on, then being turned back to the front to pick one to
    // avoid, is a small thing that feels broken.
    var bodyView by rememberSaveable { mutableStateOf(BodyView.FRONT) }
    var moreEquipment by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Onboarding is the one screen not inside the app shell's Scaffold,
            // so nothing else applies the window insets for it. Without this it
            // draws under the status bar and the camera cutout, which is what a
            // Galaxy S23 showed the first time it ran.
            .safeDrawingPadding()
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
                    detailOf = { stringResource(it.detailRes) },
                    onSelected = onGoalSelected,
                )

                OnboardingStep.EXPERIENCE -> SingleChoice(
                    options = ExperienceLevel.entries,
                    selected = state.experience,
                    labelOf = { stringResource(it.labelRes) },
                    onSelected = onExperienceSelected,
                )

                OnboardingStep.EQUIPMENT -> {
                    MultiChoice(
                        options = Equipment.COMMON,
                        isSelected = { it in state.equipment },
                        labelOf = { stringResource(it.labelRes) },
                        detailOf = { stringResource(it.detailRes) },
                        onToggled = onEquipmentToggled,
                    )

                    // The long tail is collapsed rather than dropped. Between
                    // them these account for 5% of the catalog and eight of
                    // them have a single exercise each, so they do not deserve
                    // equal billing with dumbbells — but someone who owns a
                    // sled should still be able to say so.
                    TextButton(onClick = { moreEquipment = !moreEquipment }) {
                        Text(
                            stringResource(
                                if (moreEquipment) {
                                    R.string.onboarding_equipment_fewer
                                } else {
                                    R.string.onboarding_equipment_more
                                },
                                Equipment.UNCOMMON.size,
                            ),
                        )
                    }
                    if (moreEquipment) {
                        MultiChoice(
                            options = Equipment.UNCOMMON,
                            isSelected = { it in state.equipment },
                            labelOf = { stringResource(it.labelRes) },
                            detailOf = { stringResource(it.detailRes) },
                            onToggled = onEquipmentToggled,
                        )
                    }
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

                OnboardingStep.MUSCLES -> MuscleSelector(
                    selected = state.preferredMuscles,
                    view = bodyView,
                    onViewChange = { bodyView = it },
                    onMuscleToggled = onPreferredMuscleToggled,
                    onRegionToggled = onPreferredRegionToggled,
                    labelOf = { stringResource(it.labelRes) },
                )

                OnboardingStep.AVOID -> {
                    MuscleSelector(
                        selected = state.avoidedMuscles,
                        view = bodyView,
                        onViewChange = { bodyView = it },
                        onMuscleToggled = onAvoidedMuscleToggled,
                        onRegionToggled = onAvoidedRegionToggled,
                        labelOf = { stringResource(it.labelRes) },
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
        SegmentedProgress(
            completed = state.stepNumber,
            total = state.stepCount,
            // The count is still spoken, just not written twice: the segments
            // say "4 of 7" to anyone looking, and this says it to TalkBack.
            label = stringResource(
                R.string.onboarding_step,
                state.stepNumber,
                state.stepCount,
            ),
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
    detailOf: (@Composable (T) -> String)? = null,
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
                Column(
                    modifier = Modifier.padding(Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Space.s1),
                ) {
                    Text(
                        text = labelOf(option),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    detailOf?.let { detail ->
                        Text(
                            text = detail(option),
                            style = MaterialTheme.typography.bodySmall,
                            // Inherits the card's content colour rather than
                            // taking onSurfaceVariant, which does not contrast
                            // against primaryContainer when selected.
                            color = LocalContentColor.current.copy(alpha = DETAIL_ALPHA),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Any number of answers, each explained.
 *
 * Rows rather than chips: the answer to "what is a leverage machine?" is a
 * sentence, and a sentence does not fit in a chip.
 */
@Composable
private fun <T> MultiChoice(
    options: List<T>,
    isSelected: (T) -> Boolean,
    labelOf: @Composable (T) -> String,
    detailOf: @Composable (T) -> String,
    onToggled: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        options.forEach { option ->
            val selected = isSelected(option)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Target.min)
                    .toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        onValueChange = { onToggled(option) },
                    ),
                colors = if (selected) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.padding(Space.s4),
                    horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = selected, onCheckedChange = null)
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                        Text(
                            text = labelOf(option),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = detailOf(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = DETAIL_ALPHA),
                        )
                    }
                }
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
            onValueChange = { onValueChange(it.toStepValue()) },
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
 * Progress as one segment per question, filled as they are answered.
 *
 * A continuous bar answers "roughly how far along?"; the question people
 * actually have is "how many more of these?", and seven boxes answer it without
 * a sentence underneath restating it in words.
 */
@Composable
private fun SegmentedProgress(completed: Int, total: Int, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // One description for the whole row. Without merging, TalkBack reads
            // seven anonymous boxes.
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Stroke.ring)
                    .clip(RoundedCornerShape(Radius.full))
                    .background(
                        if (index < completed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/**
 * The value a slider position stands for.
 *
 * Rounding, not truncating. A snapped stop arrives as a float built by
 * interpolation, so the sixth stop of seven is 5.9999995 rather than 6.0 â€”
 * `toInt()` floored it and the day simply could not be chosen. Every other day
 * of the week landed on an exact float, which is why it looked like one broken
 * value rather than a broken conversion.
 *
 * Internal rather than inlined at the call site so that
 * `ValueSliderConversionTest` exercises this exact function, and changing it
 * back fails a test rather than needing a device to notice.
 */
internal fun Float.toStepValue(): Int = roundToInt()

private const val SESSION_STEP_MINUTES = 5

/** Enough to read as secondary without dropping below contrast on either card colour. */
private const val DETAIL_ALPHA = 0.75f
