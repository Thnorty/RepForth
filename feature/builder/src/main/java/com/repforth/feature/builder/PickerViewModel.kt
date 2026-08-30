package com.repforth.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.Language
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
import kotlinx.coroutines.launch

data class PickerUiState(
    val query: String = "",
    val results: List<ExerciseSummary> = emptyList(),
    val selectedExercise: Exercise? = null,
    val reducedMotion: Boolean = false,
    val language: Language? = null,
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
    private val repository: ExerciseRepository,
    preferences: UserPreferencesDataSource,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedExercise = MutableStateFlow<Exercise?>(null)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val results = query
        // Same reasoning as the catalog tab: a seven-letter word would
        // otherwise run seven queries over 1,324 rows and render six lists
        // nobody reads.
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text -> repository.observeCatalog(CatalogFilter(query = text)) }

    val uiState: StateFlow<PickerUiState> =
        combine(query, results, selectedExercise, preferences.preferences) { text, matches, selected, userPrefs ->
            PickerUiState(
                query = text,
                results = matches,
                selectedExercise = selected,
                reducedMotion = userPrefs.reducedMotion,
                language = userPrefs.language,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PickerUiState(),
        )

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onSelectExercise(summary: ExerciseSummary) {
        viewModelScope.launch {
            selectedExercise.value = repository.find(summary.id)
        }
    }

    fun onDismissDetail() {
        selectedExercise.value = null
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
