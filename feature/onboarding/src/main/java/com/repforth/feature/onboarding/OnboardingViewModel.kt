package com.repforth.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.model.BodyRegion
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.allMuscles
import com.repforth.core.model.synonyms
import com.repforth.core.model.toggleRegion
import com.repforth.core.model.toggleSynonyms
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
    EQUIPMENT,
    DAYS,
    LENGTH,
    MUSCLES(optional = true),
    AVOID(optional = true),

    /**
     * Every answer, before any of it is written.
     *
     * Added because there was no way to check your own work: skipping a question
     * told you nothing about what had been recorded for you, and the profile is
     * not visible anywhere afterwards either. Finish lives here rather than on
     * the last question, so the last thing before committing is seeing what is
     * being committed.
     */
    REVIEW,
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
    /**
     * Body weight is chosen for you, and is the reason this question has no
     * Skip: everyone has their own body weight, so there is no honest empty
     * answer. Pre-selecting it also makes the saved profile identical to what
     * the screen shows, rather than something translated on the way past.
     */
    val equipment: Set<Equipment> = setOf(Equipment.BODY_WEIGHT),
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
            // Deselectable down to nothing, but not past it. Blocking here says
            // why, where silently re-adding body weight would look like a tap
            // that did not register.
            OnboardingStep.EQUIPMENT -> equipment.isNotEmpty()
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
        copy(
            preferredMuscles = preferredMuscles.toggleSynonyms(muscle),
            avoidedMuscles = avoidedMuscles - muscle.synonyms,
        )
    }

    /**
     * A muscle cannot be both preferred and avoided, so choosing either side
     * removes it from the other. Both directions, which is the whole point: it
     * was one-way, and answering Avoid then walking Back to Focus produced
     * exactly the profile this exists to prevent — one that asks the rules
     * engine to favour and forbid the same muscle.
     */
    fun onAvoidedMuscleToggled(muscle: Muscle) = update {
        copy(
            avoidedMuscles = avoidedMuscles.toggleSynonyms(muscle),
            preferredMuscles = preferredMuscles - muscle.synonyms,
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
        copy(
            preferredMuscles = preferredMuscles.toggleRegion(region),
            avoidedMuscles = avoidedMuscles - region.allMuscles(),
        )
    }

    fun onAvoidedRegionToggled(region: BodyRegion) = update {
        copy(
            avoidedMuscles = avoidedMuscles.toggleRegion(region),
            preferredMuscles = preferredMuscles - region.allMuscles(),
        )
    }

    /** Jumps straight to a question, so the review can be edited in one tap. */
    fun onJumpTo(step: OnboardingStep) = update { copy(step = step) }

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




    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
