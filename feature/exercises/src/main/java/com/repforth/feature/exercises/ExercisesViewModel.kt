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
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.MovementExclusion
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.model.Language
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the catalog screen renders. */
data class ExercisesUiState(
    val filter: CatalogFilter = CatalogFilter(),
    val results: List<ExerciseSummary> = emptyList(),
    val selectedExercise: Exercise? = null,
    val reducedMotion: Boolean = false,
    val language: Language? = null,
    val loading: Boolean = true,
    /** Exercise ids this user has ruled out; §8 keeps them out of every plan. */
    val excludedIds: Set<String> = emptySet(),
) {
    /** Whether the exercise on screen is one of them. */
    val isSelectedExcluded: Boolean
        get() = selectedExercise?.id?.value in excludedIds

    /** Distinguishes "still loading" from "nothing matches", which look alike. */
    val isEmptyResult: Boolean get() = !loading && results.isEmpty()
}

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    private val preferences: UserPreferencesDataSource,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(CatalogFilter())
    private val selectedExercise = MutableStateFlow<Exercise?>(null)

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
        combine(
            filter,
            results,
            selectedExercise,
            preferences.preferences,
            profiles.observeProfile(),
        ) { currentFilter, matches, selected, userPrefs, profile ->
            ExercisesUiState(
                filter = currentFilter,
                results = matches,
                selectedExercise = selected,
                reducedMotion = userPrefs.reducedMotion,
                language = userPrefs.language,
                loading = false,
                // Observed rather than read once, so the sheet's button flips
                // the moment the write lands instead of on the next open.
                excludedIds = profile?.exclusions.orEmpty()
                    .filter { it.kind == ExclusionKind.EXERCISE }
                    .mapTo(mutableSetOf()) { it.value },
            )
        }.stateIn(
            scope = viewModelScope,
            // Keeps the query alive briefly across a rotation, so turning the
            // phone does not re-run it and flash an empty list.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ExercisesUiState(),
        )

    fun onSelectExercise(summary: ExerciseSummary) {
        viewModelScope.launch {
            selectedExercise.value = repository.find(summary.id)
        }
    }

    fun onDismissDetail() {
        selectedExercise.value = null
    }

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

    /**
     * Excluding an exercise, from the page where the user is looking at it.
     *
     * §8 makes an exclusion a hard constraint on everything the app programmes,
     * and the model has carried [ExclusionKind.EXERCISE] since the beginning —
     * enforced by the rules engine and written by nothing at all. This is the
     * screen where somebody meets the exercise they want to rule out.
     *
     * It does not hide anything. The picker still lists it and it can still be
     * added by hand: an exclusion says what the app may programme *for* you, and
     * choosing it yourself is you overriding yourself, which is allowed.
     */
    fun onToggleExcluded(id: ExerciseId) {
        viewModelScope.launch {
            val profile = profiles.getProfile() ?: return@launch
            val existing = profile.exclusions.firstOrNull {
                it.kind == ExclusionKind.EXERCISE && it.value == id.value
            }
            profiles.save(
                profile.copy(
                    exclusions = if (existing != null) {
                        profile.exclusions - existing
                    } else {
                        profile.exclusions + MovementExclusion(ExclusionKind.EXERCISE, id.value)
                    },
                ),
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
