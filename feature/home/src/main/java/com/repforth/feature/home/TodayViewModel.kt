package com.repforth.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.common.time.TimeSource
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.SessionRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.WeekRepository
import com.repforth.core.workout.ProgressSummary
import com.repforth.core.workout.SessionSnapshot
import com.repforth.core.workout.recommendNext
import com.repforth.core.workout.toProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TodayUiState(
    /** The workout in progress, if there is one. Everything else waits behind it. */
    val active: SessionSnapshot? = null,
    /** The plan to offer, or null when none are saved. */
    val next: WorkoutTemplate? = null,
    /** When [next] was last performed, or null if never. */
    val nextLastPerformedAt: Long? = null,
    val progress: ProgressSummary = ProgressSummary(),
    val trainingDaysPerWeek: Int? = null,
    val loading: Boolean = true,
) {
    val hasPlans: Boolean get() = next != null
}

/**
 * Today (§12: the current or recommended workout, and a quick start).
 *
 * One question, answered in order: is a workout running, is there one to
 * suggest, and is there anything at all yet. Everything shown is derived from
 * the same three repositories the rest of the app reads, so Today cannot
 * disagree with Plans or Progress about what exists.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    sessions: SessionRepository,
    templates: TemplateRepository,
    profiles: ProfileRepository,
    weeks: WeekRepository,
    private val time: TimeSource,
    private val zone: ZoneId,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        sessions.observeActive(),
        sessions.observeFinished(),
        templates.observeAll(),
        profiles.observeProfile(),
        weeks.observeActive(),
    ) { active, history, plans, profile, activeWeek ->
        val next = recommendNext(plans, history, activeWeek)
        TodayUiState(
            active = active,
            next = next,
            nextLastPerformedAt = next?.let { plan ->
                history.filter { it.templateId == plan.id }.maxOfOrNull { it.startedAt }
            },
            progress = history.toProgress(time.now(), zone),
            trainingDaysPerWeek = profile?.trainingDaysPerWeek,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TodayUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
