package com.repforth.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.BodyPart
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.Muscle
import com.repforth.core.model.toggleRegion
import com.repforth.core.model.toggleSynonyms
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the catalog screen renders. */
data class ExercisesUiState(
    val filter: CatalogFilter = CatalogFilter(),
    val results: List<ExerciseSummary> = emptyList(),
    val loading: Boolean = true,
) {
    /** Distinguishes "still loading" from "nothing matches", which look alike. */
    val isEmptyResult: Boolean get() = !loading && results.isEmpty()
}

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val repository: ExerciseRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(CatalogFilter())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val results = filter
        // Typing a seven-letter word would otherwise run seven queries over
        // 1,324 rows and render six lists nobody reads. Debounce is on the
        // whole filter, not just the query, so a chip tap is coalesced too.
        .debounce { if (it.query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        // flatMapLatest, not flatMapConcat: a superseded filter's results are
        // wrong by definition, and cancelling that query is the point.
        .flatMapLatest(repository::observeCatalog)

    val uiState: StateFlow<ExercisesUiState> =
        combine(filter, results) { current, matches ->
            ExercisesUiState(filter = current, results = matches, loading = false)
        }.stateIn(
            scope = viewModelScope,
            // Keeps the query alive briefly across a rotation, so turning the
            // phone does not re-run it and flash an empty list.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ExercisesUiState(),
        )

    fun onQueryChange(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    /** Tapping the selected value clears it, so a filter needs no separate X. */
    fun onBodyPartSelected(bodyPart: BodyPart?) {
        filter.value = filter.value.copy(
            bodyPart = bodyPart.takeIf { it != filter.value.bodyPart },
        )
    }

    fun onEquipmentSelected(equipment: Equipment?) {
        filter.value = filter.value.copy(
            equipment = equipment.takeIf { it != filter.value.equipment },
        )
    }

    /**
     * Toggles the muscle's whole synonym group, not the single constant.
     *
     * `abs` and `abdominals` are one muscle under two upstream names, so
     * selecting one and leaving the other behind would produce a filter that is
     * half-applied and a chip row showing both words for the same thing.
     */
    fun onMuscleToggled(muscle: Muscle) {
        filter.value = filter.value.copy(muscles = filter.value.muscles.toggleSynonyms(muscle))
    }

    /**
     * Selecting a region is one action, not one action per muscle in it.
     *
     * Toggling each muscle individually could leave a region half-selected if
     * some of its muscles were already chosen, which reads on the map as a
     * region that will not turn off.
     */
    fun onRegionToggled(region: BodyRegion) {
        filter.value = filter.value.copy(muscles = filter.value.muscles.toggleRegion(region))
    }



    fun onClearFilters() {
        filter.value = CatalogFilter()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
