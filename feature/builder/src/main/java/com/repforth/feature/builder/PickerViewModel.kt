package com.repforth.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.ExerciseSummary
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
import kotlinx.coroutines.flow.stateIn

data class PickerUiState(
    val query: String = "",
    val results: List<ExerciseSummary> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Search over the catalog, for the picker.
 *
 * Separate from `ExercisesViewModel` rather than shared: that one owns the tab's
 * filters, its body map, and its scroll position, and hoisting all of that into
 * something both screens use would tie a modal picker's lifetime to a tab's
 * state. The query pipeline is the only part worth having in common, and it is
 * four lines.
 */
@HiltViewModel
class PickerViewModel @Inject constructor(
    repository: ExerciseRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val results = query
        // Same reasoning as the catalog tab: a seven-letter word would
        // otherwise run seven queries over 1,324 rows and render six lists
        // nobody reads.
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text -> repository.observeCatalog(CatalogFilter(query = text)) }

    val uiState: StateFlow<PickerUiState> =
        combine(query, results) { text, matches ->
            PickerUiState(query = text, results = matches, loading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PickerUiState(),
        )

    fun onQueryChange(text: String) {
        query.value = text
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
