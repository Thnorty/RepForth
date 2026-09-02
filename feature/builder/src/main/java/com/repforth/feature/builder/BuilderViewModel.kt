package com.repforth.feature.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.ai.AiGenerationFailureReason
import com.repforth.core.ai.AiPlannedExercise
import com.repforth.core.ai.AiWorkoutGenerationOutcome
import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutResponse
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.ExperienceLevel
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
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.UserProfile
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.WeekDay
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.userdata.ProfileRepository
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.WeekRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * One day being edited in a weekly plan.
 *
 * [templateId] is generated once, when the day first appears, and kept for the
 * life of the draft. It is not cosmetic: history records which template a
 * session was performed from, and Today decides which day to offer next by
 * matching those ids. Minting a fresh id on every save would silently reset
 * "which day have I not done yet" each time the week was edited.
 */
data class DraftWeekDay(
    val dayIndex: Int,
    val title: String,
    val templateId: String = UUID.randomUUID().toString(),
    val focusMuscles: List<Muscle> = emptyList(),
    val exercises: List<DraftExercise> = emptyList(),
    val isExpanded: Boolean = true,
) {
    val estimatedMinutes: Int
        get() = (exercises.sumOf { draft ->
            PlannedExercise(
                id = draft.id,
                exerciseId = draft.exerciseId,
                position = 0,
                target = draft.target,
                restMs = draft.restSeconds * 1000L,
            ).estimatedDurationMs
        } / MS_PER_MINUTE).toInt()

}

/**
 * Milliseconds in a minute.
 *
 * File-level rather than inside [DraftWeekDay]: the ViewModel converts the same
 * unit when it seeds Coach from the profile and when it writes one back.
 */
private const val MS_PER_MINUTE = 60_000L

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
    /**
     * What the provider itself said, verbatim, or null.
     *
     * Shown under the app's own explanation rather than instead of it: the
     * message says what to do, this says what happened. It is the provider's
     * text, not ours, and the dialog presents it as a quotation for that reason.
     */
    val detail: String? = null,
    /** Seconds waited, for the timeout message. Null for every other failure. */
    val waitedSeconds: Int? = null,
)

/** The rationale from a locally validated provider answer. */
data class CoachNotice(val rationale: String)

data class BuilderUiState(
    /** Null while building a new plan; set when editing a saved one. */
    val planId: String? = null,
    /**
     * The week being edited, once it has been written.
     *
     * Deliberately not [planId]: that one is a *template* id, and reusing it for
     * a week made every re-save mint a new week and leave the previous one
     * behind, so editing a week and saving it twice produced two weeks.
     */
    val weekId: String? = null,
    val name: String = "",
    val exercises: List<DraftExercise> = emptyList(),
    val weekDays: List<DraftWeekDay> = emptyList(),
    val picking: Boolean = false,
    val pickingDayIndex: Int? = null,
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
    /**
     * How many days to ask for. Seeded from the profile's training days.
     *
     * One is a real answer, not a degenerate case: someone who wants a workout
     * for this afternoon should not have to accept a whole week and delete six
     * days of it. At one, the result lands as an ordinary standalone workout
     * rather than as a week containing a single day.
     */
    val coachDays: Int = 1,
    /**
     * What this generation trains for, how experienced the trainee is, and how
     * long a day may run — seeded from the profile, changeable for one plan.
     *
     * Coach used to ask exactly one question, on the reasoning that the profile
     * already knew the rest and asking again would be asking someone to repeat
     * themselves. True of the *asking*; not true of the *showing*. Three numbers
     * shaped every generated week and none of them were on the screen that
     * generated it, so a week built for 45 minutes and a week built for 90
     * looked identical right up until the plan appeared.
     *
     * Null until the profile loads, which is also what disables the controls.
     */
    val coachGoal: TrainingGoal? = null,
    val coachExperience: ExperienceLevel? = null,
    val coachSessionMinutes: Int? = null,
    /** The profile as it stands, to tell an override from agreement. */
    val savedProfile: UserProfile? = null,
    val savingCoachDefaults: Boolean = false,
    val generating: Boolean = false,
    /** Why the last generation produced nothing, or null. */
    val coachFailure: CoachFailure? = null,
    /** Error dialog state when AI generation fails or times out. */
    val coachError: CoachError? = null,
    /** Provider rationale for successfully generated workout. */
    val coachNotice: CoachNotice? = null,
    /**
     * The exercise whose detail sheet is open, or null.
     *
     * The full [Exercise] rather than the draft row, because the sheet shows
     * instructions and animation and a draft carries neither. Loaded on tap; a
     * plan of eight would otherwise pull eight sets of instruction steps in
     * both languages to show one.
     */
    val detailExercise: Exercise? = null,
    /** From preferences, for the detail sheet's animation and instructions. */
    val reducedMotion: Boolean = false,
    val language: Language? = null,
) {
    /**
     * Whether Coach is set to something the profile does not say.
     *
     * What "Save as default" is for, and what disables it: a button that writes
     * the values already stored would look like it did nothing, because it did.
     */
    val coachDiffersFromProfile: Boolean
        get() {
            val profile = savedProfile ?: return false
            return coachGoal != profile.goal ||
                coachExperience != profile.experience ||
                coachSessionMinutes != (profile.sessionLengthMs / MS_PER_MINUTE).toInt() ||
                coachDays != profile.trainingDaysPerWeek
        }

    val isEditing: Boolean get() = planId != null
    val isWeeklyPlan: Boolean get() = weekDays.isNotEmpty()

    /** §7: a plan needs a name and something to do. */
    val canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty() && !saving
    val canSaveWeek: Boolean get() = name.isNotBlank() && weekDays.isNotEmpty() && weekDays.all { it.exercises.isNotEmpty() } && !saving

    val totalWeekMinutes: Int get() = weekDays.sumOf { it.estimatedMinutes }

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
    private val weeks: WeekRepository,
    preferences: UserPreferencesDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profiles.getProfile()
            _uiState.value = _uiState.value.copy(
                savedProfile = profile,
                coachGoal = profile?.goal,
                coachExperience = profile?.experience,
                coachSessionMinutes = profile?.let { (it.sessionLengthMs / 60_000L).toInt() },
                sessionCeilingMinutes = profile?.let { (it.sessionLengthMs / 60_000L).toInt() },
                // Seeded, not fixed. Onboarding already asked how many days a
                // week this person trains, so that is the right default; asking
                // again would be asking them to repeat themselves. Overriding it
                // for one generation is not a change to what they train.
                coachDays = profile?.trainingDaysPerWeek?.coerceIn(WorkoutLimits.days)
                    ?: _uiState.value.coachDays,
            )
        }
        viewModelScope.launch {
            preferences.preferences.collect { userPrefs ->
                update {
                    copy(reducedMotion = userPrefs.reducedMotion, language = userPrefs.language)
                }
            }
        }
    }

    /**
     * Opens the catalog entry behind a row of the plan being edited.
     *
     * The builder could show what an exercise *was* nowhere at all: the detail
     * sheet was reachable from the catalog tab and from the picker, so once a
     * row was in a plan the only way to see how to perform it was to go and
     * find it again. Noticed on a generated week, where it matters most,
     * because nobody chose those exercises by hand.
     *
     * A row whose exercise has left the catalog opens nothing rather than
     * failing: the row already renders its id as a name in that case, and the
     * plan is still editable around it.
     */
    fun onShowExerciseDetail(id: ExerciseId) {
        viewModelScope.launch {
            exercises.find(id)?.let { found -> update { copy(detailExercise = found) } }
        }
    }

    fun onDismissExerciseDetail() = update { copy(detailExercise = null) }

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

    fun onPickerOpen(dayIndex: Int? = null) = update {
        copy(picking = true, pickingDayIndex = dayIndex)
    }

    fun onPickerClose() = update { copy(picking = false, pickingDayIndex = null) }

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

    /**
     * How many days the next generation should cover.
     *
     * Clamped rather than validated: the control cannot offer anything outside
     * the range, so a value outside it is a programming error, and refusing to
     * generate would be a worse answer than generating the nearest legal week.
     */
    fun onCoachDaysChange(days: Int) = update {
        copy(coachDays = days.coerceIn(WorkoutLimits.days), coachFailure = null, coachError = null)
    }

    fun onCoachGoalChange(goal: TrainingGoal) = update {
        copy(coachGoal = goal, coachFailure = null, coachError = null)
    }

    fun onCoachExperienceChange(level: ExperienceLevel) = update {
        copy(coachExperience = level, coachFailure = null, coachError = null)
    }

    fun onCoachSessionMinutesChange(minutes: Int) = update {
        copy(
            coachSessionMinutes = minutes.coerceIn(WorkoutLimits.sessionMinutes),
            coachFailure = null,
            coachError = null,
        )
    }

    /**
     * Writes what Coach is currently set to back to the profile.
     *
     * The counterpart to the overrides: changing a value for one plan must not
     * change who the user is, so saying "and keep this" has to be a separate
     * act. It writes all four together because they are one answer to one
     * question — how this person trains — and saving three of them would leave
     * the profile in a state the user never chose.
     */
    fun onSaveCoachDefaults() {
        val state = _uiState.value
        val profile = state.savedProfile ?: return
        if (!state.coachDiffersFromProfile || state.savingCoachDefaults) return

        viewModelScope.launch {
            update { copy(savingCoachDefaults = true) }
            val updated = profile.copy(
                goal = state.coachGoal ?: profile.goal,
                experience = state.coachExperience ?: profile.experience,
                trainingDaysPerWeek = state.coachDays.coerceIn(WorkoutLimits.days),
                sessionLengthMs = (state.coachSessionMinutes ?: WorkoutLimits.sessionMinutes.last)
                    .coerceIn(WorkoutLimits.sessionMinutes)
                    .toLong() * MS_PER_MINUTE,
            )
            profiles.save(updated)
            update {
                copy(
                    savedProfile = updated,
                    savingCoachDefaults = false,
                    sessionCeilingMinutes = (updated.sessionLengthMs / MS_PER_MINUTE).toInt(),
                )
            }
        }
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
    fun onGenerate(defaultName: String, dayTitles: List<String>, locale: Language) {
        if (_uiState.value.generating) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            update { copy(generating = true, coachFailure = null, coachError = null, coachNotice = null) }

            val profile = profiles.getProfile()
            if (profile == null) {
                update { copy(generating = false, coachFailure = CoachFailure.NO_PROFILE) }
                return@launch
            }

            val current = _uiState.value
            val request = GenerationRequest(
                profile = profile,
                targetMuscles = current.coachMuscles,
                daysOverride = current.coachDays.coerceIn(WorkoutLimits.days),
                // Null where Coach agrees with the profile, so an unchanged
                // Coach produces exactly the request it produced before.
                goalOverride = current.coachGoal.takeIf { it != profile.goal },
                experienceOverride = current.coachExperience
                    .takeIf { it != profile.experience },
                sessionLengthMsOverride = current.coachSessionMinutes
                    ?.coerceIn(WorkoutLimits.sessionMinutes)
                    ?.toLong()
                    ?.times(MS_PER_MINUTE)
                    ?.takeIf { it != profile.sessionLengthMs },
            )
            val candidates = exercises.candidates()
            when (val outcome = generator.generate(
                request = request,
                locale = locale,
                candidates = candidates,
            )) {
                is AiWorkoutGenerationOutcome.Provider -> {
                    val allPlannedIds = outcome.response.days
                        .flatMap { it.exercises }
                        .map { ExerciseId(it.exerciseId) }
                    val names = exercises.summaries(allPlannedIds)
                    val draftDays = outcome.response.days.mapIndexed { dayIndex, day ->
                        DraftWeekDay(
                            dayIndex = dayIndex,
                            title = day.title.ifBlank {
                                dayTitles.getOrElse(dayIndex) { defaultName }
                            },
                            focusMuscles = day.focusMuscles.mapNotNull { slug ->
                                Muscle.entries.find {
                                    it.slug == slug || it.name.equals(slug, ignoreCase = true)
                                }
                            },
                            exercises = day.exercises.toAiDrafts(names),
                            isExpanded = (dayIndex == 0),
                        )
                    }
                    val resolvedName = _uiState.value.name.ifBlank { defaultName }

                    // A single day is a workout, not a week of one. The contract
                    // always speaks in days so there is only one schema and one
                    // validator, but storing a one-day week would fill Plans with
                    // week cards wrapping a single workout, and "start my week"
                    // would mean the same thing as "start this workout".
                    //
                    // The two lists are mutually exclusive on purpose: whichever
                    // one is populated is the one being edited, so there is never
                    // a second, stale copy of day one sitting in `exercises`.
                    val single = draftDays.singleOrNull()
                    update {
                        copy(
                            name = resolvedName,
                            source = PlanSource.AI,
                            exercises = single?.exercises.orEmpty(),
                            weekDays = if (single != null) emptyList() else draftDays,
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

    /**
     * Adds an exercise to the workout being edited, or to one day of a week.
     *
     * [dayIndex] is what tells the two apart, and it is the only difference
     * between them. Every editing operation below takes it the same way: null
     * means the standalone draft, a value means that day of the week. Writing
     * each operation twice — once for a plan and once for a day — is how this
     * class briefly grew twenty-four handlers for twelve actions, and the two
     * copies would have drifted the first time a rule changed.
     */
    fun onExerciseAdded(
        id: ExerciseId,
        name: String,
        thumbnail: MediaRef = MediaRef.Unavailable,
        dayIndex: Int? = null,
    ) = update {
        val added = DraftExercise(
            id = UUID.randomUUID().toString(),
            exerciseId = id,
            name = name,
            thumbnail = thumbnail,
        )
        withExercises(dayIndex) { it + added }
            .copy(picking = false, pickingDayIndex = null)
    }

    fun onRemove(index: Int, dayIndex: Int? = null) = update {
        withExercises(dayIndex) { if (index in it.indices) it.without(index) else it }
    }

    /**
     * Reordering by buttons rather than by dragging.
     *
     * §12 asks for spring-based card reorder, which is a drag gesture, and a
     * drag gesture is unusable with a screen reader and hard to hit at 200% font
     * scale. Buttons work for everyone; the gesture can be added on top later
     * without changing what it means to move a row.
     */
    fun onMove(from: Int, to: Int, dayIndex: Int? = null) = update {
        withExercises(dayIndex) {
            if (from in it.indices && to in it.indices && from != to) it.moved(from, to) else it
        }
    }

    fun onSetsChange(index: Int, sets: Int, dayIndex: Int? = null) =
        editExercise(dayIndex, index) { copy(sets = sets.coerceIn(SETS_RANGE)) }

    fun onRepsChange(index: Int, reps: Int, dayIndex: Int? = null) =
        editExercise(dayIndex, index) { copy(reps = reps.coerceIn(REPS_RANGE)) }

    fun onDurationChange(index: Int, seconds: Int, dayIndex: Int? = null) =
        editExercise(dayIndex, index) { copy(durationSeconds = seconds.coerceIn(DURATION_RANGE)) }

    fun onRestChange(index: Int, seconds: Int, dayIndex: Int? = null) =
        editExercise(dayIndex, index) { copy(restSeconds = seconds.coerceIn(REST_RANGE)) }

    /** Blank clears the weight, which is not the same as zero. */
    fun onWeightChange(index: Int, weightKg: Double?, dayIndex: Int? = null) =
        editExercise(dayIndex, index) { copy(weightKg = weightKg?.coerceAtLeast(0.0)) }

    fun onTimedChange(index: Int, timed: Boolean, dayIndex: Int? = null) =
        editExercise(dayIndex, index) { copy(timed = timed) }

    fun onToggleDayExpanded(dayIndex: Int) = update {
        copy(
            weekDays = weekDays.map { day ->
                if (day.dayIndex == dayIndex) day.copy(isExpanded = !day.isExpanded) else day
            },
        )
    }

    fun onDayTitleChange(dayIndex: Int, title: String) = update {
        copy(
            weekDays = weekDays.map { day ->
                if (day.dayIndex == dayIndex) day.copy(title = title) else day
            },
        )
    }

    /**
     * Writes the week and every workout inside it, in one transaction.
     *
     * [defaultName] and [dayTitles] arrive from the screen because they are
     * translated strings and this class has no resources — the same reason
     * `onSave` takes a name rather than inventing one. A hardcoded "Day 3" here
     * would be English on a Turkish phone, and it would be English in the saved
     * data rather than only on screen.
     */
    fun onSaveWeek(defaultName: String, dayTitles: List<String>) {
        val state = _uiState.value
        if (!state.canSaveWeek) return
        _uiState.value = state.copy(saving = true)
        viewModelScope.launch {
            val weekId = state.weekId ?: UUID.randomUUID().toString()
            val weekName = state.name.ifBlank { defaultName }.trim()
            val weekDays = state.weekDays.mapIndexed { index, draftDay ->
                val dayTitle = draftDay.title
                    .ifBlank { dayTitles.getOrElse(index) { defaultName } }
                    .trim()
                val template = WorkoutTemplate(
                    // The draft's id, not a new one. See DraftWeekDay.templateId.
                    id = draftDay.templateId,
                    name = dayTitle,
                    source = state.source,
                    exercises = draftDay.exercises.mapIndexed { exIndex, draftEx ->
                        PlannedExercise(
                            id = draftEx.id,
                            exerciseId = draftEx.exerciseId,
                            position = exIndex,
                            target = draftEx.target,
                            restMs = draftEx.restSeconds * 1000L,
                        )
                    },
                )
                WeekDay(
                    position = index,
                    title = dayTitle,
                    workout = template,
                )
            }
            // Becoming the active week is not automatic. A first week has
            // nothing to displace and should obviously be the one Today offers;
            // a second one silently replacing it would change what the app tells
            // you to train today without asking. Plans has a "set active" action
            // for that, which is where the decision belongs.
            val isFirstWeek = weeks.observeActive().first() == null
            val trainingWeek = TrainingWeek(
                id = weekId,
                name = weekName,
                source = state.source,
                active = isFirstWeek,
                days = weekDays,
            )
            weeks.save(trainingWeek)
            _uiState.value = _uiState.value.copy(
                saving = false,
                saved = true,
                weekId = weekId,
            )
        }
    }

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

    private fun editExercise(
        dayIndex: Int?,
        index: Int,
        block: DraftExercise.() -> DraftExercise,
    ) = update {
        withExercises(dayIndex) { list ->
            if (index !in list.indices) {
                list
            } else {
                list.mapIndexed { i, e -> if (i == index) e.block() else e }
            }
        }
    }

    /**
     * Applies one change to whichever exercise list is being edited.
     *
     * The single place that knows a standalone draft and a day of a week hold
     * the same kind of list. Everything above is a description of *what* to
     * change; this is the only thing that knows *where*.
     */
    private fun BuilderUiState.withExercises(
        dayIndex: Int?,
        block: (List<DraftExercise>) -> List<DraftExercise>,
    ): BuilderUiState = if (dayIndex == null) {
        copy(exercises = block(exercises))
    } else {
        copy(
            weekDays = weekDays.map { day ->
                if (day.dayIndex == dayIndex) day.copy(exercises = block(day.exercises)) else day
            },
        )
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
private fun List<AiPlannedExercise>.toAiDrafts(
    names: Map<ExerciseId, ExerciseSummary>,
): List<DraftExercise> = map { planned ->
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
    AiGenerationFailureReason.PROVIDER_FAILURE ->
        providerError(providerFailure, detail, deadlineSeconds)
    AiGenerationFailureReason.INVALID_RESPONSE -> CoachError(
        titleRes = R.string.coach_error_failed_title,
        messageRes = R.string.coach_error_failed_body,
        canRetry = true,
        detail = detail,
    )
}

private fun providerError(
    providerFailure: ProviderFailure?,
    detail: String?,
    deadlineSeconds: Int?,
): CoachError =
    when (providerFailure) {
        // A timeout has no server response to show, because nothing came back.
        // Saying how long it waited is the next most useful thing, and it is
        // the question the old message left open — the deadline is not fixed,
        // it grows with the number of days asked for.
        ProviderFailure.TIMEOUT -> CoachError(
            titleRes = R.string.coach_error_timeout_title,
            messageRes = R.string.coach_error_timeout_body,
            canRetry = true,
            waitedSeconds = deadlineSeconds,
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
        // A provider outage is not a broken app, and saying so matters: this
        // branch used to fall through to "could not generate a plan", which
        // reads as a problem with the request. Gemini answering 503 during a
        // demand spike is the single most likely failure here, and the honest
        // message is that the provider is busy and the plan is fine.
        ProviderFailure.SERVER -> CoachError(
            titleRes = R.string.coach_error_server_title,
            messageRes = R.string.coach_error_server_body,
            canRetry = true,
        )
        ProviderFailure.MODEL_NOT_FOUND -> CoachError(
            titleRes = R.string.coach_error_model_title,
            messageRes = R.string.coach_error_model_body,
            canRetry = false,
        )
        ProviderFailure.ENDPOINT_REFUSED -> CoachError(
            titleRes = R.string.coach_error_endpoint_title,
            messageRes = R.string.coach_error_endpoint_body,
            canRetry = false,
        )
        // FORMAT and null remain the generic case: the provider answered with
        // something unreadable, which is genuinely "something went wrong".
        else -> CoachError(
            titleRes = R.string.coach_error_failed_title,
            messageRes = R.string.coach_error_failed_body,
            canRetry = true,
        )
    }.copy(detail = detail)

private const val DEFAULT_DRAFT_REPS = 10
private const val DEFAULT_DRAFT_DURATION_SECONDS = 30
