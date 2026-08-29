package com.repforth.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
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
        const val DEFAULT_REST_SECONDS = 90
    }
}

data class BuilderUiState(
    /** Null while building a new plan; set when editing a saved one. */
    val planId: String? = null,
    val name: String = "",
    val exercises: List<DraftExercise> = emptyList(),
    val picking: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** The user's session ceiling in minutes, or null before onboarding. */
    val sessionCeilingMinutes: Int? = null,
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
                exercises = template.exercises.map { planned ->
                    val reps = planned.target as? ExerciseTarget.Reps
                    val duration = planned.target as? ExerciseTarget.Duration
                    DraftExercise(
                        id = planned.id,
                        exerciseId = planned.exerciseId,
                        name = names[planned.exerciseId]?.name ?: planned.exerciseId.value,
                        sets = planned.target.sets,
                        reps = reps?.reps ?: 10,
                        durationSeconds = ((duration?.durationMs ?: 30_000L) / 1000L).toInt(),
                        weightKg = planned.target.weightKg,
                        restSeconds = (planned.restMs / 1000L).toInt(),
                        timed = duration != null,
                    )
                },
            )
        }
    }

    fun onNameChange(name: String) = update { copy(name = name) }

    fun onPickerOpen() = update { copy(picking = true) }

    fun onPickerClose() = update { copy(picking = false) }

    fun onExerciseAdded(id: ExerciseId, name: String) = update {
        copy(
            exercises = exercises + DraftExercise(
                id = UUID.randomUUID().toString(),
                exerciseId = id,
                name = name,
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
                    source = PlanSource.MANUAL,
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
        val SETS_RANGE = 1..10
        val REPS_RANGE = 1..100
        val DURATION_RANGE = 5..3_600
        val REST_RANGE = 0..600
    }
}
