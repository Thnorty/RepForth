package com.repforth.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.common.time.TimeSource
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.workout.ProgressSummary
import com.repforth.core.workout.WorkoutSummary
import com.repforth.core.workout.mostPerformed
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.Muscle
import com.repforth.core.workout.setsPerExercise
import com.repforth.core.workout.toProgress
import com.repforth.core.workout.toSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val progress: ProgressSummary = ProgressSummary(),
    /** Newest first: the last workout is the one being looked for. */
    val workouts: List<WorkoutSummary> = emptyList(),
    /** The exercises done most often, resolved from the catalog. */
    val mostPerformed: List<String> = emptyList(),
    /**
     * The muscles trained most, by completed sets — §12's "recent muscle
     * activity".
     *
     * Muscles rather than resolved strings: their names are string resources,
     * and a view model that reached for a `Context` to read them would be the
     * one place in this app where a language change did not follow the app's
     * own language setting.
     */
    val topMuscles: List<Muscle> = emptyList(),
    /**
     * Days a week the user said they train, which is what `daysThisWeek` is
     * measured against. Null until a profile exists, and the week bar is hidden
     * rather than guessed at in that case.
     */
    val weeklyTarget: Int? = null,
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
    profiles: ProfileRepository,
    private val exercises: ExerciseRepository,
    private val time: TimeSource,
    /**
     * Injected so a streak does not depend on which machine computed it. In the
     * app this is the device's zone; in tests it is fixed.
     */
    private val zone: ZoneId,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        sessions.observeFinished(),
        profiles.observeProfile(),
    ) { history, profile ->
            HistoryUiState(
                progress = history.toProgress(time.now(), zone),
                weeklyTarget = profile?.trainingDaysPerWeek,
                // Newest first. The repository orders for storage, not for
                // reading, and the question a history answers is "what did I do
                // last?".
                workouts = history.map { it.toSummary() }.sortedByDescending { it.startedAt },
                mostPerformed = resolveNames(history.mostPerformed(TOP_EXERCISES)),
                topMuscles = resolveMuscles(history.setsPerExercise()),
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
    private suspend fun resolveNames(ids: List<ExerciseId>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val summaries = exercises.summaries(ids)
        return ids.mapNotNull { summaries[it]?.name }
    }

    /**
     * The muscles behind the sets, ranked by how many of them there were.
     *
     * The catalog is read once for every exercise in the history rather than
     * per muscle: `summaries` is a single query, and the alternative is one
     * lookup per row of a list that grows with everything the user has ever
     * done.
     *
     * Ties break on the muscle's own name so the order is stable — a list that
     * reshuffles between two equal muscles looks like data changing when
     * nothing has.
     */
    private suspend fun resolveMuscles(setsByExercise: Map<ExerciseId, Int>): List<Muscle> {
        if (setsByExercise.isEmpty()) return emptyList()
        val summaries = exercises.summaries(setsByExercise.keys)

        return setsByExercise.entries
            .mapNotNull { (id, sets) -> summaries[id]?.target?.let { it to sets } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sets) -> sets.sum() }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Muscle, Int>> { it.value }.thenBy { it.key.name })
            .take(TOP_MUSCLES)
            .map { it.key }
    }

    private companion object {
        const val TOP_EXERCISES = 5
        /** Enough to show a pattern, few enough to read at a glance. */
        const val TOP_MUSCLES = 4
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
