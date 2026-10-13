package com.repforth.feature.builder

import com.repforth.core.ai.AiGenerationFailureReason
import com.repforth.core.ai.AiPlanChange
import com.repforth.core.ai.AiPlanEditOutcome
import com.repforth.core.ai.AiPlanEditService
import com.repforth.core.ai.PlanEditApplication
import com.repforth.core.ai.PlanEditApplier
import com.repforth.core.ai.PlanEditRequest
import com.repforth.core.ai.AiWorkoutGenerationOutcome
import com.repforth.core.ai.AiWorkoutGenerationService
import com.repforth.core.ai.AiWorkoutResponse
import com.repforth.core.ai.ProviderAvailability
import com.repforth.core.ai.ProviderFailure
import com.repforth.core.exercisedata.CatalogFilter
import com.repforth.core.exercisedata.ExerciseRepository
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseSummary
import com.repforth.core.model.Language
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.userdata.TemplateRepository
import com.repforth.core.userdata.WeekRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/*
 * The builder's test doubles, shared by the view-model, plan-edit and compose
 * tests that construct a real BuilderViewModel. They were private to
 * BuilderViewModelTest until a second suite needed the same ones; a copy would
 * drift the first time a repository interface changed.
 */

internal class RecordingWeekRepository : WeekRepository {
    val saved = mutableListOf<TrainingWeek>()
    var activeId: String? = null
    private val all = MutableStateFlow<List<TrainingWeek>>(emptyList())
    private val active = MutableStateFlow<TrainingWeek?>(null)

    override fun observeAll(): Flow<List<TrainingWeek>> = all
    override fun observeActive(): Flow<TrainingWeek?> = active
    override suspend fun find(id: String): TrainingWeek? = saved.firstOrNull { it.id == id }
    override suspend fun save(week: TrainingWeek) {
        saved.removeAll { it.id == week.id }
        saved += week
        all.value = saved.toList()
    }
    override suspend fun setActive(id: String) {
        activeId = id
        active.value = saved.firstOrNull { it.id == id }
    }

    /** Lets a test say "a week is already active" without going through save. */
    var activeWeek: TrainingWeek?
        get() = active.value
        set(value) {
            active.value = value
        }
    override suspend fun delete(id: String) {
        saved.removeAll { it.id == id }
        all.value = saved.toList()
    }
    override suspend fun deleteAll() {
        saved.clear()
        all.value = emptyList()
        active.value = null
    }
}

internal class RecordingTemplateRepository : TemplateRepository {
    val saved = mutableListOf<WorkoutTemplate>()
    private val all = MutableStateFlow<List<WorkoutTemplate>>(emptyList())

    override fun observeAll(): Flow<List<WorkoutTemplate>> = all

    override suspend fun find(id: String): WorkoutTemplate? = saved.firstOrNull { it.id == id }

    override suspend fun save(template: WorkoutTemplate) {
        saved.removeAll { it.id == template.id }
        saved += template
        all.value = saved.toList()
    }

    override suspend fun delete(id: String) {
        saved.removeAll { it.id == id }
    }

    override suspend fun deleteAll() = saved.clear()
}

internal class FakeExercises : ExerciseRepository {
    var catalog: List<ExerciseCandidate> = emptyList()

    /** Full records, for the detail sheet. Empty unless a test needs one. */
    var details: Map<ExerciseId, Exercise> = emptyMap()

    override suspend fun candidates(): List<ExerciseCandidate> = catalog

    override suspend fun count(): Int = 0

    override fun observeCatalog(filter: CatalogFilter): Flow<List<ExerciseSummary>> = emptyFlow()

    override suspend fun find(id: ExerciseId): Exercise? = details[id]

    override suspend fun summaries(
        ids: Collection<ExerciseId>,
    ): Map<ExerciseId, ExerciseSummary> = ids
        .filter { it.value != "vanished" }
        .associateWith { id ->
            ExerciseSummary(
                id = id,
                name = "Exercise " + id.value.removePrefix("ex-"),
                bodyPart = BodyPart.entries.first(),
                target = Muscle.entries.first(),
                equipment = Equipment.BODY_WEIGHT,
            )
        }
}

internal class FakeWorkoutGenerator : AiWorkoutGenerationService {
    var response: AiWorkoutResponse? = null
    var failure: ProviderFailure? = null
    val locales = mutableListOf<Language>()
    val requests = mutableListOf<GenerationRequest>()

    override suspend fun generate(
        request: GenerationRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiWorkoutGenerationOutcome {
        locales += locale
        requests += request
        response?.let { return AiWorkoutGenerationOutcome.Provider(it, attempts = 1) }
        return AiWorkoutGenerationOutcome.Failure(
            reason = if (failure == null) {
                if (candidates.isEmpty()) {
                    AiGenerationFailureReason.NO_ELIGIBLE_CANDIDATES
                } else {
                    AiGenerationFailureReason.NO_PROVIDER_CONFIGURATION
                }
            } else {
                AiGenerationFailureReason.PROVIDER_FAILURE
            },
            attempts = if (failure == null) 0 else 1,
            providerFailure = failure,
        )
    }
}

/**
 * A provider is set up, which is the case every test here is about.
 *
 * The gate is covered by `CoachComposeTest` instead. Answering "configured" is
 * what the whole `ProviderAvailability` interface exists for — a view model
 * test should not have to build a keystore to say it.
 */
internal object AlwaysConfigured : ProviderAvailability {
    override val configured = kotlinx.coroutines.flow.flowOf(true)
}

/**
 * Coach's plan edit, answering from [changes] applied for real by the
 * production applier -- so a test cannot describe a proposal the app would have
 * built differently -- or with [outcome] when one is set.
 *
 * [gate], when set, holds the answer until completed, which is how a test sees
 * the Working state and what happens during it.
 */
internal class FakePlanEditService : AiPlanEditService {
    var changes: List<AiPlanChange> = emptyList()
    var rationale: String = "Because."
    var outcome: AiPlanEditOutcome? = null
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<Pair<PlanEditRequest, Language>>()

    override suspend fun edit(
        request: PlanEditRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiPlanEditOutcome {
        requests += request to locale
        gate?.await()
        outcome?.let { return it }
        val applied = PlanEditApplier.apply(request.plan, changes) as PlanEditApplication.Applied
        return AiPlanEditOutcome.Proposal(applied.edit.plan, changes, rationale, attempts = 1)
    }
}
