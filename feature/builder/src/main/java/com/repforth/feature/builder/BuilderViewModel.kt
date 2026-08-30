package com.repforth.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.ai.AiGenerationFailureReason
import com.repforth.core.ai.AiWorkoutGenerationOutcome
import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutResponse
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.Language
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.model.WorkoutLimits
import com.repforth.core.model.toggleRegion
import com.repforth.core.model.toggleSynonyms
import com.repforth.core.model.BodyRegion
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One exercise being edited.
 *
 * Holds the resolved [name] alongside the id because the plan itself stores only
 * the id, and a row that says "Bench Press" is the difference between editing a
 * workout and editing a list of identifiers. The name is display only and is
 * never written back — the catalog owns it.
 */
data class DraftExercise(
    val id: String,
    val exerciseId: ExerciseId,
    val name: String,
    val thumbnail: MediaRef = MediaRef.Unavailable,
    val sets: Int = DEFAULT_SETS,
    val reps: Int = DEFAULT_REPS,
    val durationSeconds: Int = DEFAULT_DURATION_SECONDS,
    val weightKg: Double? = null,
    val restSeconds: Int = DEFAULT_REST_SECONDS,
    val timed: Boolean = false,
) {
    /**
     * The domain target this row describes.
     *
     * [timed] chooses between two shapes that cannot both be true — a plank has
     * a duration, a curl has reps. The unused half stays in the draft rather
     * than being discarded, so switching modes twice does not lose what was
     * typed the first time.
     */
    val target: ExerciseTarget
        get() = if (timed) {
            ExerciseTarget.Duration(sets, durationSeconds * 1000L, weightKg)
        } else {
            ExerciseTarget.Reps(sets, reps, weightKg)
        }

    private companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS = 10
        const val DEFAULT_DURATION_SECONDS = 30
        const val DEFAULT_REST_SECONDS = 30
    }
}

/**
 * Why Coach came back empty.
 *
 * A generated plan that fails silently is indistinguishable from one that is
 * still thinking, and "no exercises matched" is useless to someone who has
 * excluded most of the catalog without realising. The engine records a reason
 * per rejected candidate (§8); this is the one that dominated, which is the
 * one worth putting on screen.
 */
enum class CoachFailure {
    /** Onboarding has not run, so there are no constraints to build from. */
    NO_PROFILE,
    EQUIPMENT,
    EXCLUSIONS,
    MUSCLES,
    /** The catalog is empty, or nothing fitted the session length. */
    NOTHING,
}

data class CoachError(
    val titleRes: Int,
    val messageRes: Int,
    val canRetry: Boolean,
)

/** The rationale from a locally validated provider answer. */
data class CoachNotice(val rationale: String)

data class BuilderUiState(
    /** Null while building a new plan; set when editing a saved one. */
    val planId: String? = null,
    val name: String = "",
    val exercises: List<DraftExercise> = emptyList(),
    val picking: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** How this plan first entered the builder; retained if the user edits it. */
    val source: PlanSource = PlanSource.MANUAL,
    /** The user's session ceiling in minutes, or null before onboarding. */
    val sessionCeilingMinutes: Int? = null,
    /** Coach's sheet is open. */
    val coaching: Boolean = false,
    /** Muscles asked for. Empty means "anything my profile allows". */
    val coachMuscles: Set<Muscle> = emptySet(),
    val generating: Boolean = false,
    /** Why the last generation produced nothing, or null. */
    val coachFailure: CoachFailure? = null,
    /** Error dialog state when AI generation fails or times out. */
    val coachError: CoachError? = null,
    /** Provider rationale for successfully generated workout. */
    val coachNotice: CoachNotice? = null,
) {
    val isEditing: Boolean get() = planId != null

    /** §7: a plan needs a name and something to do. */
    val canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty() && !saving

    /**
     * Rest is known exactly and work is not, so this is an estimate and the
     * screen says "about". It exists to be compared against the session length
     * from onboarding, which is the constraint a plan can actually violate.
     */
    val estimatedMinutes: Int
        get() = (estimatedMs / MS_PER_MINUTE).toInt()

    val exceedsCeiling: Boolean
        get() = sessionCeilingMinutes?.let { estimatedMinutes > it } == true

    private val estimatedMs: Long
        get() = exercises.sumOf { draft ->
            PlannedExercise(
                id = draft.id,
                exerciseId = draft.exerciseId,
                position = 0,
                target = draft.target,
                restMs = draft.restSeconds * 1000L,
            ).estimatedDurationMs
        }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}

/**
 * The manual workout builder (§3, §12).
 *
 * §12 puts this behind Plans and Today rather than in the bottom bar, and
 * requires a plan to be editable cards rather than a transcript — so the whole
 * screen is a list of rows that can be reordered and retyped, and the same
 * screen edits a saved plan as builds a new one.
 *
 * Coach, the AI mode §12 describes, is Phase 2. Nothing here assumes it is
 * absent: a generated plan arrives as the same draft list this produces, which
 * is what makes "editable before starting" a property of the builder rather
 * than something the AI path has to reimplement.
 */
@HiltViewModel
class BuilderViewModel @Inject constructor(
    private val templates: TemplateRepository,
    private val exercises: ExerciseRepository,
    private val profiles: ProfileRepository,
    private val generator: AiWorkoutGenerationService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val minutes = profiles.getProfile()?.let { (it.sessionLengthMs / 60_000L).toInt() }
            _uiState.value = _uiState.value.copy(sessionCeilingMinutes = minutes)
        }
    }

    /**
     * Loads a saved plan for editing.
     *
     * Names are resolved in one query rather than one per row. An exercise the
     * catalog no longer has keeps its id as a name instead of vanishing: losing
     * a row silently would let someone save a shorter plan than the one they
     * opened, without being told.
     */
    fun load(planId: String) {
        if (_uiState.value.planId == planId) return
        viewModelScope.launch {
            val template = templates.find(planId) ?: return@launch
            val names = exercises.summaries(template.exercises.map { it.exerciseId })
            _uiState.value = _uiState.value.copy(
                planId = template.id,
                name = template.name,
                source = template.source,
                exercises = template.exercises.toDrafts(names),
            )
        }
    }

    fun onNameChange(name: String) = update { copy(name = name) }

    fun onPickerOpen() = update { copy(picking = true) }

    fun onPickerClose() = update { copy(picking = false) }

    private var generationJob: Job? = null

    fun onCoachOpen() = update {
        copy(coaching = true, coachFailure = null, coachError = null, coachNotice = null)
    }

    fun onCoachClose() {
        generationJob?.cancel()
        generationJob = null
        update { copy(coaching = false, coachFailure = null, coachError = null, generating = false) }
    }

    fun onCancelGenerate() {
        generationJob?.cancel()
        generationJob = null
        update { copy(generating = false, coachError = null) }
    }

    fun onDismissCoachError() = update {
        copy(coachError = null)
    }

    /**
     * Toggling one muscle toggles its synonyms with it.
     *
     * The catalog names the same muscle more than one way, and a request for
     * "pecs" that leaves "chest" unselected would silently exclude half the
     * exercises someone just asked for. The rule lives in `core:model` so the
     * onboarding and Coach cannot drift apart about what a muscle is.
     */
    fun onCoachMuscleToggled(muscle: Muscle) = update {
        copy(coachMuscles = coachMuscles.toggleSynonyms(muscle), coachFailure = null, coachError = null)
    }

    fun onCoachRegionToggled(region: BodyRegion) = update {
        copy(coachMuscles = coachMuscles.toggleRegion(region), coachFailure = null, coachError = null)
    }

    /**
     * Builds a plan through the validated AI provider pipeline (§3, §8).
     *
     * The result lands as the same draft list the manual path produces, so it
     * is editable before it is saved and nothing is written until the user says
     * so. That is the point of putting Coach inside the builder rather than
     * beside it: a generated plan is a starting point, not a decision.
     *
     * [defaultName] comes from the screen because it is a translated string and
     * this class has no resources. It is used only when the field is empty —
     * overwriting a name someone typed would be the generator taking a decision
     * that was not its to take.
     */
    fun onGenerate(defaultName: String, locale: Language) {
        if (_uiState.value.generating) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            update { copy(generating = true, coachFailure = null, coachError = null, coachNotice = null) }

            val profile = profiles.getProfile()
            if (profile == null) {
                update { copy(generating = false, coachFailure = CoachFailure.NO_PROFILE) }
                return@launch
            }

            val request = GenerationRequest(
                profile = profile,
                targetMuscles = _uiState.value.coachMuscles,
            )
            val candidates = exercises.candidates()
            val planName = _uiState.value.name.ifBlank { defaultName }
            when (val outcome = generator.generate(
                request = request,
                locale = locale,
                candidates = candidates,
            )) {
                is AiWorkoutGenerationOutcome.Provider -> {
                    val ids = outcome.response.exercises.map { ExerciseId(it.exerciseId) }
                    val names = exercises.summaries(ids)
                    update {
                        copy(
                            name = planName,
                            source = PlanSource.AI,
                            exercises = outcome.response.toDrafts(names),
                            generating = false,
                            coaching = false,
                            coachFailure = null,
                            coachError = null,
                            coachNotice = CoachNotice(outcome.response.rationale),
                        )
                    }
                }

                is AiWorkoutGenerationOutcome.Failure -> {
                    update {
                        copy(
                            generating = false,
                            coachError = outcome.toCoachError(),
                        )
                    }
                }
            }
        }
    }

    fun onExerciseAdded(
        id: ExerciseId,
        name: String,
        thumbnail: MediaRef = MediaRef.Unavailable,
    ) = update {
        copy(
            exercises = exercises + DraftExercise(
                id = UUID.randomUUID().toString(),
                exerciseId = id,
                name = name,
                thumbnail = thumbnail,
            ),
            picking = false,
        )
    }

    fun onRemove(index: Int) = update {
        if (index !in exercises.indices) this else copy(exercises = exercises.without(index))
    }

    /**
     * Reordering by buttons rather than by dragging.
     *
     * §12 asks for spring-based card reorder, which is a drag gesture, and a
     * drag gesture is unusable with a screen reader and hard to hit at 200% font
     * scale. Buttons work for everyone; the gesture can be added on top later
     * without changing what it means to move a row.
     */
    fun onMove(from: Int, to: Int) = update {
        if (from !in exercises.indices || to !in exercises.indices || from == to) {
            this
        } else {
            copy(exercises = exercises.moved(from, to))
        }
    }

    fun onSetsChange(index: Int, sets: Int) = edit(index) {
        copy(sets = sets.coerceIn(SETS_RANGE))
    }

    fun onRepsChange(index: Int, reps: Int) = edit(index) {
        copy(reps = reps.coerceIn(REPS_RANGE))
    }

    fun onDurationChange(index: Int, seconds: Int) = edit(index) {
        copy(durationSeconds = seconds.coerceIn(DURATION_RANGE))
    }

    fun onRestChange(index: Int, seconds: Int) = edit(index) {
        copy(restSeconds = seconds.coerceIn(REST_RANGE))
    }

    /** Blank clears the weight, which is not the same as zero. */
    fun onWeightChange(index: Int, weightKg: Double?) = edit(index) {
        copy(weightKg = weightKg?.coerceAtLeast(0.0))
    }

    fun onTimedChange(index: Int, timed: Boolean) = edit(index) { copy(timed = timed) }

    /**
     * Writes the plan, renumbering positions from zero.
     *
     * [WorkoutTemplate] requires contiguous positions in order and throws
     * otherwise, so the renumber is not tidiness — a plan whose rows were moved
     * would fail to construct. Doing it here means the draft never has to keep
     * positions correct while being edited.
     */
    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.value = state.copy(saving = true)
        viewModelScope.launch {
            templates.save(
                WorkoutTemplate(
                    id = state.planId ?: UUID.randomUUID().toString(),
                    name = state.name.trim(),
                    source = state.source,
                    exercises = state.exercises.mapIndexed { index, draft ->
                        PlannedExercise(
                            id = draft.id,
                            exerciseId = draft.exerciseId,
                            position = index,
                            target = draft.target,
                            restMs = draft.restSeconds * 1000L,
                        )
                    },
                ),
            )
            _uiState.value = _uiState.value.copy(saving = false, saved = true)
        }
    }

    private fun edit(index: Int, block: DraftExercise.() -> DraftExercise) = update {
        if (index !in exercises.indices) {
            this
        } else {
            copy(exercises = exercises.mapIndexed { i, e -> if (i == index) e.block() else e })
        }
    }

    private fun update(block: BuilderUiState.() -> BuilderUiState) {
        _uiState.value = _uiState.value.block()
    }

    private fun <T> List<T>.without(index: Int): List<T> =
        toMutableList().apply { removeAt(index) }

    private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
        toMutableList().apply { add(to, removeAt(from)) }

    internal companion object {
        val SETS_RANGE = WorkoutLimits.sets
        val REPS_RANGE = WorkoutLimits.reps
        val DURATION_RANGE = WorkoutLimits.durationSeconds
        val REST_RANGE = WorkoutLimits.restSeconds
    }
}

/**
 * A saved or generated plan, as editable rows.
 *
 * One mapping, used by both paths. A generated plan is not a different kind of
 * thing from a stored one — §12 asks for it to be editable before it starts,
 * and the cheapest way to guarantee that is for it to arrive as the same type
 * through the same function.
 *
 * An exercise the catalog no longer has keeps its id as a name rather than
 * vanishing: losing a row silently would let someone save a shorter plan than
 * the one they opened, without being told.
 */
private fun List<PlannedExercise>.toDrafts(
    names: Map<ExerciseId, ExerciseSummary>,
): List<DraftExercise> = map { planned ->
    val reps = planned.target as? ExerciseTarget.Reps
    val duration = planned.target as? ExerciseTarget.Duration
    DraftExercise(
        id = planned.id,
        exerciseId = planned.exerciseId,
        name = names[planned.exerciseId]?.name ?: planned.exerciseId.value,
        thumbnail = names[planned.exerciseId]?.thumbnail ?: MediaRef.Unavailable,
        sets = planned.target.sets,
        reps = reps?.reps ?: DEFAULT_DRAFT_REPS,
        durationSeconds = (
            (duration?.durationMs ?: DEFAULT_DRAFT_DURATION_SECONDS * 1000L) / 1000L
        ).toInt(),
        weightKg = planned.target.weightKg,
        restSeconds = (planned.restMs / 1000L).toInt(),
        timed = duration != null,
    )
}

/** A validated provider answer, projected without changing any target value. */
private fun AiWorkoutResponse.toDrafts(
    names: Map<ExerciseId, ExerciseSummary>,
): List<DraftExercise> = exercises.map { planned ->
    val exerciseId = ExerciseId(planned.exerciseId)
    DraftExercise(
        id = UUID.randomUUID().toString(),
        exerciseId = exerciseId,
        name = names[exerciseId]?.name ?: exerciseId.value,
        thumbnail = names[exerciseId]?.thumbnail ?: MediaRef.Unavailable,
        sets = planned.sets,
        reps = planned.repetitions ?: DEFAULT_DRAFT_REPS,
        durationSeconds = planned.durationSeconds ?: DEFAULT_DRAFT_DURATION_SECONDS,
        weightKg = planned.weightKg,
        restSeconds = planned.restSeconds,
        timed = planned.durationSeconds != null,
    )
}

private fun AiWorkoutGenerationOutcome.Failure.toCoachError(): CoachError = when (reason) {
    AiGenerationFailureReason.NO_PROVIDER_CONFIGURATION -> CoachError(
        titleRes = R.string.coach_error_no_config_title,
        messageRes = R.string.coach_error_no_config_body,
        canRetry = false,
    )
    AiGenerationFailureReason.NO_PROVIDER_ADAPTER -> CoachError(
        titleRes = R.string.coach_error_no_adapter_title,
        messageRes = R.string.coach_error_no_adapter_body,
        canRetry = false,
    )
    AiGenerationFailureReason.NO_ELIGIBLE_CANDIDATES -> CoachError(
        titleRes = R.string.coach_error_no_candidates_title,
        messageRes = R.string.coach_error_no_candidates_body,
        canRetry = false,
    )
    AiGenerationFailureReason.PROVIDER_FAILURE -> when (providerFailure) {
        ProviderFailure.TIMEOUT -> CoachError(
            titleRes = R.string.coach_error_timeout_title,
            messageRes = R.string.coach_error_timeout_body,
            canRetry = true,
        )
        ProviderFailure.NETWORK -> CoachError(
            titleRes = R.string.coach_error_network_title,
            messageRes = R.string.coach_error_network_body,
            canRetry = true,
        )
        ProviderFailure.AUTHENTICATION -> CoachError(
            titleRes = R.string.coach_error_auth_title,
            messageRes = R.string.coach_error_auth_body,
            canRetry = false,
        )
        ProviderFailure.QUOTA -> CoachError(
            titleRes = R.string.coach_error_quota_title,
            messageRes = R.string.coach_error_quota_body,
            canRetry = true,
        )
        else -> CoachError(
            titleRes = R.string.coach_error_failed_title,
            messageRes = R.string.coach_error_failed_body,
            canRetry = true,
        )
    }
    AiGenerationFailureReason.INVALID_RESPONSE -> CoachError(
        titleRes = R.string.coach_error_failed_title,
        messageRes = R.string.coach_error_failed_body,
        canRetry = true,
    )
}

private const val DEFAULT_DRAFT_REPS = 10
private const val DEFAULT_DRAFT_DURATION_SECONDS = 30
