package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.model.ProviderId
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.rules.RulesEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class AiGenerationFailureReason {
    NO_ELIGIBLE_CANDIDATES,
    NO_PROVIDER_CONFIGURATION,
    NO_PROVIDER_ADAPTER,
    PROVIDER_FAILURE,
    INVALID_RESPONSE,
}

/** A provider result that passed every local check, or a failure outcome. */
sealed interface AiWorkoutGenerationOutcome {
    data class Provider(
        val response: AiWorkoutResponse,
        val attempts: Int,
    ) : AiWorkoutGenerationOutcome

    data class Failure(
        val reason: AiGenerationFailureReason,
        val attempts: Int,
        val providerFailure: ProviderFailure? = null,
    ) : AiWorkoutGenerationOutcome
}

/**
 * The generation boundary consumed by UI features.
 *
 * Depending on the interface keeps Coach tests independent of encrypted
 * settings and HTTP adapters while the production binding still has exactly
 * one implementation.
 */
interface AiWorkoutGenerationService {
    suspend fun generate(
        request: GenerationRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiWorkoutGenerationOutcome
}

/**
 * The Phase 2.4 pipeline from local constraints to one trusted result (§8).
 *
 * Provider configuration is resolved for this call and never retained. The
 * candidate catalog is reduced by [RulesEngine] before it crosses the network;
 * provider output then comes back through [AiWorkoutValidator]. Only malformed
 * or locally invalid output gets the single repair attempt.
 */
@Singleton
class AiWorkoutGenerator @Inject constructor(
    private val repository: ProviderRepository,
    private val providers: Map<ProviderId, @JvmSuppressWildcards AiProvider>,
) : AiWorkoutGenerationService {
    private val rules = RulesEngine()
    private val validator = AiWorkoutValidator(rules)

    override suspend fun generate(
        request: GenerationRequest,
        locale: Language,
        candidates: List<ExerciseCandidate>,
    ): AiWorkoutGenerationOutcome {
        val filtered = rules.filterCandidates(request, candidates)
        if (filtered.eligibleCandidates.isEmpty()) {
            return AiWorkoutGenerationOutcome.Failure(
                reason = AiGenerationFailureReason.NO_ELIGIBLE_CANDIDATES,
                attempts = 0,
            )
        }

        val settings = repository.settings.first()
        val config = repository.configFor(settings)
            ?: return AiWorkoutGenerationOutcome.Failure(
                reason = AiGenerationFailureReason.NO_PROVIDER_CONFIGURATION,
                attempts = 0,
            )
        val provider = providers[config.provider]
            ?: return AiWorkoutGenerationOutcome.Failure(
                reason = AiGenerationFailureReason.NO_PROVIDER_ADAPTER,
                attempts = 0,
            )
        val providerRequest = AiWorkoutRequest.from(
            request = request,
            locale = locale,
            eligibleCandidates = filtered.eligibleCandidates,
        )

        val first = provider.generateWorkout(config, providerRequest)
        return when (first) {
            is ProviderGenerationResult.Ok -> {
                val validation = validator.validate(
                    first.response,
                    request,
                    filtered.eligibleCandidates,
                )
                if (validation.isValid) {
                    AiWorkoutGenerationOutcome.Provider(
                        response = requireNotNull(validation.response),
                        attempts = 1,
                    )
                } else {
                    retry(
                        provider = provider,
                        config = config,
                        providerRequest = providerRequest,
                        retryFeedback = AiWorkoutRetryFeedback.from(validation),
                        request = request,
                        eligibleCandidates = filtered.eligibleCandidates,
                    )
                }
            }

            is ProviderGenerationResult.Failed -> {
                if (first.failure == ProviderFailure.FORMAT) {
                    retry(
                        provider = provider,
                        config = config,
                        providerRequest = providerRequest,
                        retryFeedback = AiWorkoutRetryFeedback.Malformed,
                        request = request,
                        eligibleCandidates = filtered.eligibleCandidates,
                    )
                } else {
                    AiWorkoutGenerationOutcome.Failure(
                        reason = AiGenerationFailureReason.PROVIDER_FAILURE,
                        attempts = 1,
                        providerFailure = first.failure,
                    )
                }
            }
        }
    }

    private suspend fun retry(
        provider: AiProvider,
        config: com.repforth.core.model.ProviderConfig,
        providerRequest: AiWorkoutRequest,
        retryFeedback: AiWorkoutRetryFeedback,
        request: GenerationRequest,
        eligibleCandidates: List<ExerciseCandidate>,
    ): AiWorkoutGenerationOutcome {
        return when (val second = provider.generateWorkout(config, providerRequest, retryFeedback)) {
            is ProviderGenerationResult.Ok -> {
                val validation = validator.validate(second.response, request, eligibleCandidates)
                if (validation.isValid) {
                    AiWorkoutGenerationOutcome.Provider(
                        response = requireNotNull(validation.response),
                        attempts = 2,
                    )
                } else {
                    AiWorkoutGenerationOutcome.Failure(
                        reason = AiGenerationFailureReason.INVALID_RESPONSE,
                        attempts = 2,
                    )
                }
            }

            is ProviderGenerationResult.Failed -> AiWorkoutGenerationOutcome.Failure(
                reason = if (second.failure == ProviderFailure.FORMAT) {
                    AiGenerationFailureReason.INVALID_RESPONSE
                } else {
                    AiGenerationFailureReason.PROVIDER_FAILURE
                },
                attempts = 2,
                providerFailure = second.failure,
            )
        }
    }
}
