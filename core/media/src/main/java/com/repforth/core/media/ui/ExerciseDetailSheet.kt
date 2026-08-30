package com.repforth.core.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import com.repforth.core.media.R
import com.repforth.core.model.Exercise
import com.repforth.core.model.Language

/**
 * Shared exercise detail sheet displaying media, muscles, equipment, attribution, and step instructions.
 *
 * Inset below status bars and punch holes so the drag handle and header are never obscured.
 * Features a bottom fade-out gradient to visually indicate scrollable content above pinned actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: Exercise,
    reducedMotion: Boolean,
    language: Language?,
    targetLabel: String,
    equipmentLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryMuscleLabels: List<String> = emptyList(),
    bottomAction: @Composable (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = Space.s2),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Layout.gutterPhone)
                .padding(bottom = Space.s4),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            label = { Text(targetLabel) },
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(equipmentLabel) },
                        )
                    }

                    if (secondaryMuscleLabels.isNotEmpty()) {
                        val secondaryNames = secondaryMuscleLabels.joinToString(", ")
                        Text(
                            text = stringResource(R.string.exercise_detail_secondary_muscles, secondaryNames),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Step-by-step instructions
                    Text(
                        text = stringResource(R.string.exercise_detail_instructions),
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

                    // Trailing space inside scrollable area so bottom-most instruction is never obscured by fade
                    Spacer(modifier = Modifier.height(Space.s6))
                }

                // Fade-out scrim overlay at the bottom of the scrollable region
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(Space.s6)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    BottomSheetDefaults.ContainerColor,
                                ),
                            ),
                        ),
                )
            }

            // Pinned Bottom Actions (always visible)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.s2),
            ) {
                if (bottomAction != null) {
                    bottomAction()
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Target.min),
                    ) {
                        Text(stringResource(R.string.exercise_detail_close))
                    }
                }
            }
        }
    }
}
