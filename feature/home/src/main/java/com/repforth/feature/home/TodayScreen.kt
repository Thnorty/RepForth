package com.repforth.feature.home

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.workout.SessionSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Today (§12): the current or recommended workout, and a quick start.
 *
 * Answers one question in order of urgency — is a workout running, is there one
 * to suggest, is there anything saved at all — so the first thing on screen is
 * always the next thing to do rather than a summary of what has been done.
 */
@Composable
fun TodayRoute(
    onResumeWorkout: () -> Unit,
    onStartPlan: (String) -> Unit,
    onBuildWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TodayScreen(
        state = state,
        onResumeWorkout = onResumeWorkout,
        onStartPlan = onStartPlan,
        onBuildWorkout = onBuildWorkout,
        modifier = modifier,
    )
}

@Composable
internal fun TodayScreen(
    state: TodayUiState,
    onResumeWorkout: () -> Unit,
    onStartPlan: (String) -> Unit,
    onBuildWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Layout.gutterPhone, vertical = Space.s3),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        // A workout in progress outranks everything. Someone who opens the app
        // mid-session is not looking for a suggestion.
        state.active?.let { active ->
            item(key = "resume") {
                ResumeCard(active = active, onResume = onResumeWorkout)
            }
        }

        if (state.active == null) {
            val next = state.next
            if (next != null) {
                item(key = "next") {
                    NextCard(
                        state = state,
                        onStart = { onStartPlan(next.id) },
                    )
                }
            } else if (!state.loading) {
                item(key = "empty") { EmptyCard(onBuildWorkout = onBuildWorkout) }
            }
        }

        if (!state.loading) {
            item(key = "week") { WeekCard(state) }
        }
    }
}

@Composable
private fun ResumeCard(active: SessionSnapshot, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(
                text = stringResource(R.string.today_resume_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.today_resume_body,
                    active.currentExerciseIndex + 1,
                    active.exercises.size,
                    active.currentSetIndex + 1,
                    active.currentExercise?.target?.sets ?: 0,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().heightIn(min = Target.session),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.today_resume))
            }
        }
    }
}

@Composable
private fun NextCard(state: TodayUiState, onStart: () -> Unit) {
    val next = state.next ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Text(
                text = stringResource(R.string.today_next),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = next.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(
                    R.string.today_plan_summary,
                    next.exercises.size,
                    (next.estimatedDurationMs / 60_000L).toInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // Says why this one is being offered. A recommendation with no
                // reason reads as arbitrary, and this one's reason is simple.
                text = state.nextLastPerformedAt?.let {
                    stringResource(R.string.today_last_done, formatDate(it))
                } ?: stringResource(R.string.today_never_done),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Target.session)
                    .padding(top = Space.s2),
            ) {
                Text(stringResource(R.string.today_start))
            }
        }
    }
}

@Composable
private fun EmptyCard(onBuildWorkout: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.today_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onBuildWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Target.min)
                    .padding(top = Space.s2),
            ) {
                Text(stringResource(R.string.today_build))
            }
        }
    }
}

@Composable
private fun WeekCard(state: TodayUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Text(
                text = stringResource(R.string.today_week),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.progress.workoutsThisWeek == 0 && state.progress.streakWeeks == 0) {
                Text(
                    text = stringResource(R.string.today_week_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s8),
            ) {
                Column {
                    Text(
                        text = state.progress.workoutsThisWeek.toString(),
                        style = RepForthNumeric.md,
                    )
                    Text(
                        // Against the goal from onboarding when there is one:
                        // "2 of 4 days" answers a question that "2 done" does
                        // not.
                        text = state.trainingDaysPerWeek?.let { target ->
                            stringResource(
                                R.string.today_week_of_target,
                                state.progress.workoutsThisWeek,
                                target,
                            )
                        } ?: stringResource(
                            R.string.today_week_count,
                            state.progress.workoutsThisWeek,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.progress.streakWeeks > 0) {
                    Column {
                        Text(
                            text = state.progress.streakWeeks.toString(),
                            style = RepForthNumeric.md,
                        )
                        Text(
                            text = stringResource(
                                R.string.today_streak,
                                state.progress.streakWeeks,
                            ),
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
private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
