package com.repforth.core.ai

import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId

/**
 * A provider that answers from a fixture instead of the network (§8).
 *
 * In `main` rather than a test source set on purpose: §8 asks for it for "tests
 * and previews", and a Compose preview cannot see `src/test`. It ships in the
 * APK, which costs a few hundred bytes and is not reachable — nothing binds it.
 *
 * It is deliberately not clever. A fake that decided for itself whether a key
 * looked valid would be asserting the provider's rules, which this project does
 * not know and cannot keep up to date; [next] is set by whoever is testing, so
 * the expectation lives in the test that has it.
 */
class FakeAiProvider(
    override val id: ProviderId = ProviderId.GEMINI,
    var next: ProviderTestResult = ProviderTestResult.Ok(modelConfirmed = true),
    var nextWorkout: ProviderGenerationResult = ProviderGenerationResult.Failed(
        ProviderFailure.FORMAT,
        "No workout fixture configured",
    ),
) : AiProvider {

    /** Every config it was called with, so a test can assert what was sent. */
    val calls = mutableListOf<ProviderConfig>()

    /** Requests are separate so a connection assertion cannot consume generation state. */
    val workoutCalls = mutableListOf<Pair<ProviderConfig, AiWorkoutRequest>>()

    override suspend fun testConnection(config: ProviderConfig): ProviderTestResult {
        calls += config
        return next
    }

    override suspend fun generateWorkout(
        config: ProviderConfig,
        request: AiWorkoutRequest,
    ): ProviderGenerationResult {
        workoutCalls += config to request
        return nextWorkout
    }
}
