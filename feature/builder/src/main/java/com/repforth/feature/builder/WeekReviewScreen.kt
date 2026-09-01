package com.repforth.feature.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.repforth.core.designsystem.component.RfIcons
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.model.ExerciseId

/**
 * Multi-day weekly plan review screen (§5, `docs/WEEKLY_PLANS.md`).
 *
 * Displays AI Coach's weekly programming structured into collapsible daily
 * accordion cards. The user can customize day titles, reorder/edit/remove
 * exercises in any day, add extra exercises to a specific day, and save the
 * complete week as an active program.
 */
@Composable
internal fun WeekReviewScreen(
    state: BuilderUiState,
    onWeekNameChange: (String) -> Unit,
    onDayTitleChange: (Int, String) -> Unit,
    onToggleDayExpanded: (Int) -> Unit,
    onAddExerciseToDay: (Int) -> Unit,
    onOpenExerciseDetail: (ExerciseId) -> Unit,
    onRemoveExerciseFromDay: (Int, Int) -> Unit,
    onMoveExerciseInDay: (Int, Int, Int) -> Unit,
    onSetsChangeInDay: (Int, Int, Int) -> Unit,
    onRepsChangeInDay: (Int, Int, Int) -> Unit,
    onDurationChangeInDay: (Int, Int, Int) -> Unit,
    onRestChangeInDay: (Int, Int, Int) -> Unit,
    onWeightChangeInDay: (Int, Int, Double?) -> Unit,
    onTimedChangeInDay: (Int, Int, Boolean) -> Unit,
    onSaveWeek: () -> Unit,
    onCoach: () -> Unit,
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
            item(key = "title") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onWeekNameChange,
                    label = { Text(stringResource(R.string.builder_name_label)) },
                    placeholder = { Text(stringResource(R.string.coach_week_default_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item(key = "summary") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(stringResource(R.string.week_summary_days, state.weekDays.size))
                        },
                    )
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(stringResource(R.string.week_summary_duration, state.totalWeekMinutes))
                        },
                    )
                }
            }

            state.coachNotice?.let { notice ->
                item(key = "notice") {
                    CoachNoticeCard(notice = notice)
                }
            }

            items(state.weekDays.size, key = { state.weekDays[it].dayIndex }) { dayIdx ->
                val day = state.weekDays[dayIdx]
                DayAccordionCard(
                    day = day,
                    onToggleExpanded = { onToggleDayExpanded(day.dayIndex) },
                    onTitleChange = { onDayTitleChange(day.dayIndex, it) },
                    onAddExercise = { onAddExerciseToDay(day.dayIndex) },
                    onOpenExerciseDetail = onOpenExerciseDetail,
                    onRemoveExercise = { exIdx -> onRemoveExerciseFromDay(day.dayIndex, exIdx) },
                    onMoveExercise = { from, to -> onMoveExerciseInDay(day.dayIndex, from, to) },
                    onSetsChange = { exIdx, sets -> onSetsChangeInDay(day.dayIndex, exIdx, sets) },
                    onRepsChange = { exIdx, reps -> onRepsChangeInDay(day.dayIndex, exIdx, reps) },
                    onDurationChange = { exIdx, sec -> onDurationChangeInDay(day.dayIndex, exIdx, sec) },
                    onRestChange = { exIdx, rest -> onRestChangeInDay(day.dayIndex, exIdx, rest) },
                    onWeightChange = { exIdx, wt -> onWeightChangeInDay(day.dayIndex, exIdx, wt) },
                    onTimedChange = { exIdx, timed -> onTimedChangeInDay(day.dayIndex, exIdx, timed) },
                )
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

        WeekReviewFooter(state = state, onSave = onSaveWeek)
    }
}

@Composable
private fun DayAccordionCard(
    day: DraftWeekDay,
    onToggleExpanded: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAddExercise: () -> Unit,
    onOpenExerciseDetail: (ExerciseId) -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onMoveExercise: (Int, Int) -> Unit,
    onSetsChange: (Int, Int) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onDurationChange: (Int, Int) -> Unit,
    onRestChange: (Int, Int) -> Unit,
    onWeightChange: (Int, Double?) -> Unit,
    onTimedChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s3),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = Space.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = weekDayLabel(day.dayIndex, day.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.week_day_exercises_count, day.exercises.size) +
                            " · " +
                            stringResource(R.string.builder_estimate, day.estimatedMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.heightIn(min = Target.min),
                ) {
                    Icon(
                        painter = if (day.isExpanded) RfIcons.Collapse else RfIcons.Expand,
                        contentDescription = null,
                    )
                }
            }

            if (day.isExpanded) {
                OutlinedTextField(
                    value = day.title,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.week_day_title_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                day.exercises.forEachIndexed { index, draft ->
                    ExerciseCard(
                        draft = draft,
                        index = index,
                        total = day.exercises.size,
                        onOpenDetail = { onOpenExerciseDetail(draft.exerciseId) },
                        onRemove = { onRemoveExercise(index) },
                        onMove = { to -> onMoveExercise(index, to) },
                        onSetsChange = { onSetsChange(index, it) },
                        onRepsChange = { onRepsChange(index, it) },
                        onDurationChange = { onDurationChange(index, it) },
                        onRestChange = { onRestChange(index, it) },
                        onWeightChange = { onWeightChange(index, it) },
                        onTimedChange = { onTimedChange(index, it) },
                    )
                }

                OutlinedButton(
                    onClick = onAddExercise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Target.min),
                ) {
                    Text(stringResource(R.string.week_day_add_exercise))
                }
            }
        }
    }
}

@Composable
private fun WeekReviewFooter(
    state: BuilderUiState,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        if (state.weekDays.isNotEmpty()) {
            Text(
                text = stringResource(R.string.week_summary_duration, state.totalWeekMinutes),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Button(
            onClick = onSave,
            enabled = state.canSaveWeek,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Target.min),
        ) {
            Text(stringResource(R.string.week_builder_save))
        }
    }
}
