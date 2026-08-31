package com.repforth.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.common.time.TimeSource
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.workout.ProgressSummary
import com.repforth.core.workout.WorkoutSummary
import com.repforth.core.workout.mostPerformed
import com.repforth.core.workout.toProgress
import com.repforth.core.workout.toSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val progress: ProgressSummary = ProgressSummary(),
    /** Newest first: the last workout is the one being looked for. */
    val workouts: List<WorkoutSummary> = emptyList(),
    /** Exercise names for [ProgressSummary.topMuscles], resolved from the catalog. */
    val mostPerformed: List<String> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && workouts.isEmpty()
}

/**
 * The Progress tab (§12: history, streaks, volume, recent muscle activity).
 *
 * Every figure is derived from the sessions on each emission rather than stored.
 * A workout has tens of sets, and a history is tens of workouts, so recomputing
 * costs nothing measurable — while a stored total is a second source of truth
 * that goes wrong the first time a session is edited, imported, or deleted.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val time: TimeSource,
    /**
     * Injected so a streak does not depend on which machine computed it. In the
     * app this is the device's zone; in tests it is fixed.
     */
    private val zone: ZoneId,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = sessions.observeFinished()
        .map { history ->
            HistoryUiState(
                progress = history.toProgress(time.now(), zone),
                // Newest first. The repository orders for storage, not for
                // reading, and the question a history answers is "what did I do
                // last?".
                workouts = history.map { it.toSummary() }.sortedByDescending { it.startedAt },
                mostPerformed = resolveNames(history.mostPerformed(TOP_EXERCISES)),
                loading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryUiState(),
        )

    /**
     * Names for the most-performed exercises, in the order they were ranked.
     *
     * An id the catalog no longer has is dropped here rather than shown as a
     * raw id: this is a summary, not a record, and a line of provenance in a
     * "most performed" list helps nobody. The session itself still keeps the id.
     */
    private suspend fun resolveNames(ids: List<com.repforth.core.model.ExerciseId>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val summaries = exercises.summaries(ids)
        return ids.mapNotNull { summaries[it]?.name }
    }

    private companion object {
        const val TOP_EXERCISES = 5
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
