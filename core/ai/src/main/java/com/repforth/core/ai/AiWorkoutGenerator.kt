package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.model.ProviderId
import com.repforth.core.rules.GenerationOutcome
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.rules.RulesEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class AiFallbackReason {
    NO_ELIGIBLE_CANDIDATES,
    NO_PROVIDER_CONFIGURATION,
    NO_PROVIDER_ADAPTER,
    PROVIDER_FAILURE,
    INVALID_RESPONSE,
}

/** A provider result that passed every local check, or the deterministic path. */
sealed interface AiWorkoutGenerationOutcome {
    data class Provider(
        val response: AiWorkoutResponse,
        val attempts: Int,
    ) : AiWorkoutGenerationOutcome

    data class Rules(
        val generation: GenerationOutcome,
        val reason: AiFallbackReason,
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
        planName: String,
    ): AiWorkoutGenerationOutcome
}

/**
 * The Phase 2.4 pipeline from local constraints to one trusted result (§8).
 *
 * Provider configuration is resolved for this call and never retained. The
 * candidate catalog is reduced by [RulesEngine] before it crosses the network;
 * provider output then comes back through [AiWorkoutValidator]. Only malformed
 * or locally invalid output gets the single repair attempt. Every other failure
 * goes straight to the deterministic generator, so AI is never a requirement.
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
        planName: String,
    ): AiWorkoutGenerationOutcome {
        val filtered = rules.filterCandidates(request, candidates)
        if (filtered.eligibleCandidates.isEmpty()) {
            return fallback(
                request,
                candidates,
                planName,
                AiFallbackReason.NO_ELIGIBLE_CANDIDATES,
                attempts = 0,
            )
        }

        val settings = repository.settings.first()
        val config = repository.configFor(settings)
            ?: return fallback(
                request,
                candidates,
                planName,
                AiFallbackReason.NO_PROVIDER_CONFIGURATION,
                attempts = 0,
            )
        val provider = providers[config.provider]
            ?: return fallback(
                request,
                candidates,
                planName,
                AiFallbackReason.NO_PROVIDER_ADAPTER,
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
                        allCandidates = candidates,
                        planName = planName,
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
                        allCandidates = candidates,
                        planName = planName,
                    )
                } else {
                    fallback(
                        request,
                        candidates,
                        planName,
                        AiFallbackReason.PROVIDER_FAILURE,
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
        allCandidates: List<ExerciseCandidate>,
        planName: String,
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
                    fallback(
                        request,
                        allCandidates,
                        planName,
                        AiFallbackReason.INVALID_RESPONSE,
                        attempts = 2,
                    )
                }
            }

            is ProviderGenerationResult.Failed -> fallback(
                request,
                allCandidates,
                planName,
                reason = if (second.failure == ProviderFailure.FORMAT) {
                    AiFallbackReason.INVALID_RESPONSE
                } else {
                    AiFallbackReason.PROVIDER_FAILURE
                },
                attempts = 2,
                providerFailure = second.failure,
            )
        }
    }

    private fun fallback(
        request: GenerationRequest,
        candidates: List<ExerciseCandidate>,
        planName: String,
        reason: AiFallbackReason,
        attempts: Int,
        providerFailure: ProviderFailure? = null,
    ) = AiWorkoutGenerationOutcome.Rules(
        generation = rules.generate(request, candidates, planName),
        reason = reason,
        attempts = attempts,
        providerFailure = providerFailure,
    )
}
