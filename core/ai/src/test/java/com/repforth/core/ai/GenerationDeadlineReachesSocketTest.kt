package com.repforth.core.ai

import com.repforth.core.ai.http.ProviderHttp
import com.repforth.core.model.ProviderConfig
import com.repforth.core.model.ProviderId
import com.repforth.core.model.ProviderSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scaled deadline actually reaches the socket.
 *
 * `GenerationTimeoutTest` proves the arithmetic; this proves it is *wired*, and
 * the difference is the whole reason this file exists. The arithmetic was right
 * once before while the call site still passed the unscaled value, and the only
 * symptom on a device was a timeout that looked exactly like a slow provider.
 *
 * Real sockets and a real clock, so the delays are kept small: a 1-second base
 * budget against a server that stalls for 3 seconds. One day must give up; six
 * days must not, because six seconds of budget outlasts the stall.
 */
class GenerationDeadlineReachesSocketTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val http = ProviderHttp(OkHttpClient())
    private lateinit var gemini: GeminiProvider

    @Before
    fun setUp() {
        server.start()
        gemini = GeminiProvider(http, json, server.url("/v1beta/").toString())
    }

    @After
    fun tearDown() = server.close()

    /**
     * The deadline is longer for a bigger request, measured against a real clock.
     *
     * Nothing is enqueued, so MockWebServer accepts the connection and never
     * answers — the cleanest way to make a call sit until its own deadline ends
     * it. Both calls must therefore time out; what is being asserted is that the
     * six-day one took materially longer to do so.
     */
    @Test
    fun `the deadline reaching the socket grows with the number of days`() = runTest {
        val oneDay = timeToGiveUp(days = 1)
        val sixDays = timeToGiveUp(days = 6)

        assertTrue(
            "A one-day request should give up near its 1s budget, took ${oneDay}ms",
            oneDay < 2_500,
        )
        assertTrue(
            "A six-day request must outlast a one-day budget several times over, " +
                "took ${sixDays}ms against ${oneDay}ms. If these are alike, the " +
                "scaled deadline is not reaching the socket.",
            sixDays > oneDay + 2_500,
        )
    }

    /** Milliseconds until the adapter gave up, asserting that it gave up at all. */
    private suspend fun timeToGiveUp(days: Int): Long {
        val startedAt = System.nanoTime()
        val result = gemini.generateWorkout(config(), request(days = days))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertEquals(
            ProviderFailure.TIMEOUT,
            (result as ProviderGenerationResult.Failed).failure,
        )
        return elapsedMs
    }

    private fun config() = ProviderConfig(
        settings = ProviderSettings.Default.copy(
            provider = ProviderId.GEMINI,
            model = "gemini-3.5-flash",
            requestTimeoutSeconds = 1,
        ),
        apiKey = "test-not-a-real-key",
    )

    private fun request(days: Int) = AiWorkoutRequest(
        schemaVersion = AI_WORKOUT_SCHEMA_VERSION,
        locale = "en",
        goal = "hypertrophy",
        experience = "beginner",
        days = days,
        sessionDurationMinutes = 40,
        maxExercisesPerDay = com.repforth.core.model.WorkoutLimits.maxExercisesPerDay,
        primaryMuscles = listOf("pectorals"),
        secondaryMuscles = emptyList(),
        excludedMuscles = emptyList(),
        excludedExerciseIds = emptyList(),
        excludedMovements = emptyList(),
        equipment = listOf("dumbbell"),
        candidateExercises = listOf(
            AiExerciseCandidate("exercise-a", "pectorals", "dumbbell", AiTargetType.REPETITIONS),
        ),
    )

    private companion object {
        val VALID_WORKOUT = """
            {"schema_version":$AI_WORKOUT_SCHEMA_VERSION,"days":[{"day_index":0,
            "title":"Push","focus_muscles":[],"exercises":[{"exercise_id":"exercise-a",
            "order":0,"sets":3,"repetitions":10,"duration_seconds":null,
            "weight_kg":null,"rest_seconds":60,"tempo":null}]}],"rationale":"ok"}
        """.trimIndent().replace("\n", "")
    }
}
