package com.repforth.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.userdata.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The questions, in order, and which of them may be left unanswered.
 *
 * An enum rather than an integer, so a step cannot be added without the `when`
 * that renders it failing to compile. [optional] drives whether Skip appears —
 * the last two shape a plan but a person with no answer to them should not be
 * stuck at the door.
 */
enum class OnboardingStep(val optional: Boolean = false) {
    GOAL,
    EXPERIENCE,
    EQUIPMENT(optional = true),
    DAYS,
    LENGTH,
    MUSCLES(optional = true),
    AVOID(optional = true),
    ;

    companion object {
        val ordered: List<OnboardingStep> = entries
    }
}

/**
 * The answers so far.
 *
 * [goal] and [experience] are nullable because "not yet answered" is a real
 * state that [UserProfile] refuses to represent — its constructor requires both.
 * That refusal is the point: the incomplete shape lives here and only here, and
 * the domain type cannot be built until it is complete.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.GOAL,
    val goal: TrainingGoal? = null,
    val experience: ExperienceLevel? = null,
    val equipment: Set<Equipment> = emptySet(),
    val trainingDaysPerWeek: Int = DEFAULT_DAYS,
    val sessionLengthMinutes: Int = DEFAULT_SESSION_MINUTES,
    val preferredMuscles: Set<Muscle> = emptySet(),
    val avoidedMuscles: Set<Muscle> = emptySet(),
    val saving: Boolean = false,
) {
    val stepNumber: Int get() = OnboardingStep.ordered.indexOf(step) + 1
    val stepCount: Int get() = OnboardingStep.ordered.size
    val isFirstStep: Boolean get() = stepNumber == 1
    val isLastStep: Boolean get() = stepNumber == stepCount

    /**
     * Whether Next is allowed. Only the two questions with no sensible default
     * can block: equipment defaults to bodyweight, and the sliders always hold
     * a legal value, so there is nothing to withhold.
     */
    val canAdvance: Boolean
        get() = when (step) {
            OnboardingStep.GOAL -> goal != null
            OnboardingStep.EXPERIENCE -> experience != null
            else -> true
        }

    companion object {
        const val DEFAULT_DAYS = 3
        const val DEFAULT_SESSION_MINUTES = 45

        /** §3: a session is between a quarter of an hour and two hours. */
        val SESSION_MINUTES_RANGE = 15..120
        val DAYS_RANGE = 1..7
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onGoalSelected(goal: TrainingGoal) = update { copy(goal = goal) }

    fun onExperienceSelected(level: ExperienceLevel) = update { copy(experience = level) }

    fun onEquipmentToggled(equipment: Equipment) = update {
        copy(equipment = this.equipment.toggle(equipment))
    }

    fun onDaysChanged(days: Int) = update {
        copy(trainingDaysPerWeek = days.coerceIn(OnboardingUiState.DAYS_RANGE))
    }

    fun onSessionLengthChanged(minutes: Int) = update {
        copy(sessionLengthMinutes = minutes.coerceIn(OnboardingUiState.SESSION_MINUTES_RANGE))
    }

    /**
     * Toggles the muscle's whole synonym group, matching the catalog filters.
     *
     * `abs` and `abdominals` are one muscle under two upstream names. Selecting
     * one and leaving the other would write a preference that is half-applied,
     * and the rules engine would honour half of it.
     */
    fun onPreferredMuscleToggled(muscle: Muscle) = update {
        copy(preferredMuscles = preferredMuscles.toggleGroup(muscle))
    }

    /**
     * A muscle cannot be both preferred and avoided, so choosing one side
     * removes it from the other. The alternative is a profile that asks the
     * rules engine to both favour and forbid the same thing.
     */
    fun onAvoidedMuscleToggled(muscle: Muscle) = update {
        val group = synonymGroup(muscle)
        copy(
            avoidedMuscles = avoidedMuscles.toggleGroup(muscle),
            preferredMuscles = preferredMuscles - group,
        )
    }

    /**
     * Selecting a region is one action, not one action per muscle in it.
     *
     * Toggling each muscle individually could leave a region half-selected when
     * some of its muscles were already chosen, which reads on the map as a
     * region that will not turn off.
     */
    fun onPreferredRegionToggled(region: BodyRegion) = update {
        copy(preferredMuscles = preferredMuscles.toggleRegion(region))
    }

    fun onAvoidedRegionToggled(region: BodyRegion) = update {
        val group = region.muscles.flatMapTo(mutableSetOf(), ::synonymGroup)
        copy(
            avoidedMuscles = avoidedMuscles.toggleRegion(region),
            preferredMuscles = preferredMuscles - group,
        )
    }

    fun onBack() = update {
        if (isFirstStep) this else copy(step = OnboardingStep.ordered[stepNumber - 2])
    }

    fun onNext() = update {
        if (isLastStep || !canAdvance) this else copy(step = OnboardingStep.ordered[stepNumber])
    }

    /** Skip is Next with the current step's answers left as they are. */
    fun onSkip() = onNext()

    /**
     * Writes the profile.
     *
     * Nothing navigates afterwards, deliberately: the app decides what to show
     * from whether a profile exists, so the write is the transition. A callback
     * here could disagree with the database, and then the user would land on a
     * home screen the app does not believe it has a profile for.
     */
    fun onFinish() {
        val state = _uiState.value
        val goal = state.goal ?: return
        val experience = state.experience ?: return
        if (state.saving) return

        _uiState.value = state.copy(saving = true)
        viewModelScope.launch {
            profiles.save(
                UserProfile(
                    id = UUID.randomUUID().toString(),
                    goal = goal,
                    experience = experience,
                    trainingDaysPerWeek = state.trainingDaysPerWeek,
                    sessionLengthMs = state.sessionLengthMinutes * MS_PER_MINUTE,
                    availableEquipment = state.equipment,
                    preferredMuscles = state.preferredMuscles,
                    exclusions = state.avoidedMuscles
                        .map { MovementExclusion(ExclusionKind.MUSCLE, it.slug) }
                        .toSet(),
                ),
            )
        }
    }

    private fun update(block: OnboardingUiState.() -> OnboardingUiState) {
        _uiState.value = _uiState.value.block()
    }

    private fun synonymGroup(muscle: Muscle): Set<Muscle> =
        Muscle.entries.filterTo(mutableSetOf()) { it.canonical == muscle.canonical }

    private fun Set<Muscle>.toggleGroup(muscle: Muscle): Set<Muscle> {
        val group = synonymGroup(muscle)
        return if (containsAll(group)) this - group else this + group
    }

    private fun Set<Muscle>.toggleRegion(region: BodyRegion): Set<Muscle> {
        val group = region.muscles.flatMapTo(mutableSetOf(), ::synonymGroup)
        return if (containsAll(group)) this - group else this + group
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
