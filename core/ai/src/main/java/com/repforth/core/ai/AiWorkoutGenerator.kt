package com.repforth.core.ai

import com.repforth.core.model.ExerciseCandidate
import com.repforth.core.model.Language
import com.repforth.core.model.ProviderId
import com.repforth.core.rules.GenerationRequest
import com.repforth.core.ai.http.generationTimeoutSeconds
import com.repforth.core.rules.RulesEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
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
        /** What the provider said, verbatim and bounded. Null when it said nothing. */
        val detail: String? = null,
        /**
         * The deadline this attempt was actually given, in seconds.
         *
         * Present for a timeout, so the message can say how long it waited.
         * "It timed out" invites the question this field answers, and the
         * answer changes with the number of days requested.
         */
        val deadlineSeconds: Int? = null,
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

        val deadline = generationTimeoutSeconds(
            config.settings.requestTimeoutSeconds,
            providerRequest.days,
        )
        val first = callProvider(provider, config, providerRequest, null)
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
                        deadline = deadline,
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
                        deadline = deadline,
                    )
                } else {
                    AiWorkoutGenerationOutcome.Failure(
                        reason = AiGenerationFailureReason.PROVIDER_FAILURE,
                        attempts = 1,
                        providerFailure = first.failure,
                        detail = first.detail,
                        deadlineSeconds = deadline,
                    )
                }
            }
        }
    }

    /**
     * One logical provider call, retrying only transport-level transience.
     *
     * This is a different thing from the repair attempt below, and conflating
     * the two is what made a provider outage look like a broken app. A repair
     * attempt is for output the model got *wrong*: it costs tokens, it may only
     * happen once (§8), and it needs feedback to be worth making. A 5xx is not
     * wrong output — it is no output. Gemini answers a demand spike with
     * `503 UNAVAILABLE` and the words "spikes in demand are usually temporary",
     * nothing is billed, and the identical request a second later commonly
     * succeeds. Failing the whole generation on the first one turns a blip into
     * a dead feature, which is exactly what it did.
     *
     * Bounded and backed off rather than persistent: the user is watching a
     * spinner, the generation card offers cancel, and an app that hammers a
     * provider that just said it is overloaded is part of the problem.
     */
    private suspend fun callProvider(
        provider: AiProvider,
        config: com.repforth.core.model.ProviderConfig,
        providerRequest: AiWorkoutRequest,
        retryFeedback: AiWorkoutRetryFeedback?,
    ): ProviderGenerationResult {
        var backoffMs = FIRST_BACKOFF_MS
        repeat(TRANSIENT_ATTEMPTS - 1) {
            val result = provider.generateWorkout(config, providerRequest, retryFeedback)
            val transient = result is ProviderGenerationResult.Failed &&
                result.failure == ProviderFailure.SERVER
            if (!transient) return result
            delay(backoffMs)
            backoffMs *= 2
        }
        return provider.generateWorkout(config, providerRequest, retryFeedback)
    }

    private suspend fun retry(
        provider: AiProvider,
        config: com.repforth.core.model.ProviderConfig,
        providerRequest: AiWorkoutRequest,
        retryFeedback: AiWorkoutRetryFeedback,
        request: GenerationRequest,
        eligibleCandidates: List<ExerciseCandidate>,
        deadline: Int,
    ): AiWorkoutGenerationOutcome {
        return when (val second = callProvider(provider, config, providerRequest, retryFeedback)) {
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
                detail = second.detail,
                deadlineSeconds = deadline,
            )
        }
    }

    private companion object {
        /** Total tries for one call, including the first. */
        const val TRANSIENT_ATTEMPTS = 3
        const val FIRST_BACKOFF_MS = 1_000L
    }
}
