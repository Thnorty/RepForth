package com.repforth.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.WeekRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The saved plan library. Reads and deletes; building happens in the builder. */
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val templates: TemplateRepository,
    private val weeks: WeekRepository,
) : ViewModel() {

    val plans: StateFlow<List<WorkoutTemplate>> = templates.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    val weeklyPlans: StateFlow<List<TrainingWeek>> = weeks.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * Deletes the plan. Sessions performed from it are kept, which is the
     * repository's contract: history is a record of what happened, and deleting
     * the plan does not unmake the workouts.
     */
    fun onDelete(id: String) {
        viewModelScope.launch { templates.delete(id) }
    }

    fun onDeleteWeek(id: String) {
        viewModelScope.launch { weeks.delete(id) }
    }

    fun onSetActiveWeek(id: String) {
        viewModelScope.launch { weeks.setActive(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
