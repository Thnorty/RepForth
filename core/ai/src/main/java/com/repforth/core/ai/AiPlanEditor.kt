package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.rules.RulesEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** A plan edit that applied and passed every local check, or why there is none. */
sealed interface AiPlanEditOutcome {
    /**
     * [edited] is the plan with [changes] applied; [changes] is what the review
     * lists. An empty [changes] is a valid answer, explained by [rationale].
     */
    data class Proposal(
        val edited: EditablePlan,
        val changes: List<AiPlanChange>,
        val rationale: String,
        val attempts: Int,
    ) : AiPlanEditOutcome

    /**
     * Generation's failure, wrapped rather than copied: the reasons, provider
     * categories and deadline are the same facts, and the builder already knows
     * how to put each one on screen.
     */
    data class Failure(val failure: AiWorkoutGenerationOutcome.Failure) : AiPlanEditOutcome
}

/** The plan-edit boundary consumed by the builder, so its tests can fake it. */
interface AiPlanEditService {
    suspend fun edit(
        request: PlanEditRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiPlanEditOutcome
}

/**
 * One plan edit from request to trusted proposal, built like
 * [AiWorkoutGenerator] and sharing its rules: configuration resolved for this
 * call and never kept, the catalog filtered locally before it crosses the
 * network, a server error retried without spending the repair attempt, and one
 * repair attempt for an answer that is malformed, does not apply, or does not
 * validate -- with feedback written by this app, never quoted from the provider.
 */
@Singleton
class AiPlanEditor @Inject constructor(
    private val repository: ProviderRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards AiProvider>,
) : AiPlanEditService {
    private val rules = RulesEngine()

    override suspend fun edit(
        request: PlanEditRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiPlanEditOutcome {
        // No muscle filter: "add chest" and "something for the same muscles"
        // both need the whole catalog the person's equipment reaches.
        val eligible = rules
            .filterCandidates(GenerationRequest(profile = request.profile), candidates)
            .eligibleCandidates
        if (eligible.isEmpty()) return failure(AiGenerationFailureReason.NO_ELIGIBLE_CANDIDATES, 0)

        val settings = repository.settings.first()
        val config = repository.configFor(settings)
            ?: return failure(AiGenerationFailureReason.NO_PROVIDER_CONFIGURATION, 0)
        val provider = providers[config.provider]
            ?: return failure(AiGenerationFailureReason.NO_PROVIDER_ADAPTER, 0)

        val providerRequest = AiPlanEditRequest.from(request, locale, candidates, eligible)
        val offered = eligible.associateBy { it.id.value }
        val deadline = config.settings.requestTimeoutSeconds

        var feedback: AiWorkoutRetryFeedback? = null
        for (attempt in 1..2) {
            when (val result = call(provider, config, providerRequest, feedback)) {
                is ProviderPlanEditResult.Failed -> {
                    if (result.failure != ProviderFailure.FORMAT) {
                        return failure(
                            AiGenerationFailureReason.PROVIDER_FAILURE,
                            attempt,
                            result.failure,
                            result.detail,
                            deadline,
                        )
                    }
                    if (attempt == 2) {
                        return failure(
                            AiGenerationFailureReason.INVALID_RESPONSE,
                            attempt,
                            result.failure,
                            result.detail,
                            deadline,
                        )
                    }
                    feedback = AiWorkoutRetryFeedback.Malformed
                }

                is ProviderPlanEditResult.Ok -> {
                    val violations = judge(request.plan, result.response, offered)
                    if (violations.isEmpty()) {
                        val applied = PlanEditApplier.apply(request.plan, result.response.changes)
                            as PlanEditApplication.Applied
                        return AiPlanEditOutcome.Proposal(
                            edited = applied.edit.plan,
                            changes = result.response.changes,
                            rationale = result.response.rationale.trim(),
                            attempts = attempt,
                        )
                    }
                    if (attempt == 2) return failure(AiGenerationFailureReason.INVALID_RESPONSE, attempt)
                    feedback = AiWorkoutRetryFeedback.fromContract(violations)
                }
            }
        }
        error("unreachable: the second attempt always returns")
    }

    /** Every reason this answer cannot be proposed, from applying it and then judging it. */
    private fun judge(
        plan: EditablePlan,
        response: AiPlanEditResponse,
        offered: Map<String, ExerciseCandidate>,
    ): List<AiWorkoutContractViolation> =
        when (val applied = PlanEditApplier.apply(plan, response.changes)) {
            is PlanEditApplication.Rejected -> applied.violations
            is PlanEditApplication.Applied -> PlanEditValidator.validate(
                original = plan,
                edit = applied.edit,
                rationale = response.rationale,
                offered = offered,
            )
        }

    private suspend fun call(
        provider: AiProvider,
        config: ProviderConfig,
        request: AiPlanEditRequest,
        feedback: AiWorkoutRetryFeedback?,
    ): ProviderPlanEditResult = retryingServerErrors(
        isServerError = { it is ProviderPlanEditResult.Failed && it.failure == ProviderFailure.SERVER },
    ) {
        provider.editPlan(config, request, feedback)
    }

    private fun failure(
        reason: AiGenerationFailureReason,
        attempts: Int,
        providerFailure: ProviderFailure? = null,
        detail: String? = null,
        deadlineSeconds: Int? = null,
    ) = AiPlanEditOutcome.Failure(
        AiWorkoutGenerationOutcome.Failure(
            reason = reason,
            attempts = attempts,
            providerFailure = providerFailure,
            detail = detail,
            deadlineSeconds = deadlineSeconds,
        ),
    )
}
