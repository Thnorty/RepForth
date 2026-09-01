package com.repforth.core.ai

import com.repforth.core.model.WorkoutLimits
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The provider-visible name changes when the contract changes. */
const val AI_WORKOUT_SCHEMA_NAME = "repforth_workout_v$AI_WORKOUT_SCHEMA_VERSION"

/**
 * The one JSON Schema supplied to every structured-output provider (§8).
 *
 * **Shape only — no bounds.** No `minimum`, `maximum`, `minItems` or
 * `maxItems`, and the omission is load-bearing rather than an oversight.
 *
 * Gemini rejected the bounded version of this schema outright with
 * `400 INVALID_ARGUMENT: Request contains an invalid argument`, naming nothing.
 * It was measured rather than guessed: `tools/probe-gemini-schema.ps1` and
 * `tools/probe-gemini-fields.ps1` bisect a schema against the live endpoint,
 * and the deciding run showed the full schema failing 3/3 while the identical
 * schema with every bound stripped passed 3/3. Dropping *fields* did not help;
 * dropping bounds did. Four earlier explanations — an unsupported keyword,
 * nullable-at-depth, a per-object property budget, and a flaky endpoint — were
 * each contradicted by the next measurement, so this comment records only what
 * was reproduced.
 *
 * Nothing is lost by it. [AiWorkoutValidator] already enforces every one of
 * these ranges and is authoritative in any case, because a compatible server is
 * free to ignore the schema entirely — and the ranges are stated in the prompt,
 * so the model is still told what they are. The values continue to come from
 * [WorkoutLimits], written once.
 *
 * Re-run the probes before adding a keyword back. No JVM test can catch this:
 * MockWebServer accepts any schema at all.
 */
object AiWorkoutJsonSchema {
    val value: JsonObject = objectSchema(
        properties = linkedMapOf(
            "days" to arraySchema(daySchema()),
            "rationale" to stringSchema(),
        ),
    )

    private fun daySchema() = objectSchema(
        properties = linkedMapOf(
            "title" to stringSchema(),
            "focus_muscles" to arraySchema(stringSchema()),
            "exercises" to arraySchema(exerciseSchema()),
        ),
    )

    private fun exerciseSchema() = objectSchema(
        properties = linkedMapOf(
            "exercise_id" to stringSchema(),
            "sets" to integerSchema(),
            "repetitions" to nullable("integer"),
            "duration_seconds" to nullable("integer"),
            "weight_kg" to nullable("number"),
            "rest_seconds" to integerSchema(),
        ),
    )

    private fun stringSchema() = buildJsonObject { put("type", "string") }

    private fun integerSchema() = buildJsonObject { put("type", "integer") }

    private fun arraySchema(items: JsonObject) = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    private fun objectSchema(properties: LinkedHashMap<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("properties", JsonObject(properties))
        put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
    }

    /** A value that may be absent, as a type array — the form both providers accept. */
    private fun nullable(type: String) = buildJsonObject {
        put(
            "type",
            buildJsonArray {
                add(JsonPrimitive(type))
                add(JsonPrimitive("null"))
            },
        )
    }
}

/**
 * One prompt shape, regardless of which provider carries it.
 *
 * Written as headed sections of plain instructions rather than prose wrapped
 * around an encoded request object. The earlier version stated each constraint
 * twice — once in a sentence and again inside the JSON dump appended to it —
 * which is the same duplication this repo forbids everywhere else, and it left
 * the model to work out which copy governed.
 *
 * The section that matters most is the time budget. [AiWorkoutValidator] rejects
 * any day whose estimate exceeds the session ceiling, using a formula the model
 * was never told: sets × (reps × [WorkoutLimits.secondsPerRepEstimate] + rest),
 * counting the last set's rest. A model cannot hit a budget it cannot compute,
 * so it was failing a check it had no way to pass and spending the single repair
 * attempt on it. The formula is now stated, in the same words the validator uses.
 */
internal fun AiWorkoutRequest.toGenerationPrompt(
    retryFeedback: AiWorkoutRetryFeedback? = null,
): String = buildString {
    appendLine("You are programming a training week for one person. Follow this brief exactly.")
    appendLine()

    appendLine("BRIEF")
    appendLine(
        "- produce exactly $days ${if (days == 1) "day" else "days"}, " +
            "in order, in the days array",
    )
    appendLine("- goal: $goal")
    appendLine("- training experience: $experience")
    appendLine("- time available per day: $sessionDurationMinutes minutes")
    appendLine(
        if (primaryMuscles.isEmpty()) {
            "- muscles to work: unspecified, so choose a sensible balance from the catalog"
        } else {
            "- muscles to work: ${primaryMuscles.joinToString(", ")}"
        },
    )
    if (secondaryMuscles.isNotEmpty()) {
        appendLine("- muscles this person also likes to train: ${secondaryMuscles.joinToString(", ")}")
    }
    if (excludedMovements.isNotEmpty()) {
        appendLine(
            "- never program these movement patterns, whatever the catalog offers: " +
                excludedMovements.joinToString("; "),
        )
    }
    appendLine("- write title and rationale in this language: $locale")
    appendLine()

    appendLine("SHAPE OF EACH DAY")
    appendLine("1. Open with 1-2 warm-up movements for the muscles that day trains.")
    appendLine("2. Then the working exercises: compound and heaviest first, isolation last.")
    appendLine("3. Close with 1-2 stretches for the muscles that day worked.")
    appendLine(
        "The catalog names say which is which: a row named \"... stretch\" is a stretch, " +
            "and mobility or rotation movements make good warm-ups.",
    )
    appendLine()

    appendLine("SHAPE OF THE WEEK")
    appendLine("- Give every requested muscle at least one day.")
    appendLine(
        "- Do not train the same muscle hard on consecutive days. The " +
            "secondary_muscles column shows the overlap you would otherwise miss.",
    )
    appendLine("- The same exercise may appear on different days, but never twice in one day.")
    appendLine()

    appendLine("TIME BUDGET - this is checked, and a day over budget is rejected")
    appendLine(
        "A day costs the sum over its exercises of " +
            "sets x (repetitions x ${WorkoutLimits.secondsPerRepEstimate} + rest_seconds) seconds, " +
            "or sets x (duration_seconds + rest_seconds) for timed ones. " +
            "The last set's rest counts.",
    )
    appendLine("Keep each day's total at or under ${sessionDurationMinutes * 60} seconds.")
    appendLine()

    appendLine("NUMBERS - all checked on arrival, anything outside is rejected")
    appendLine("- at most ${WorkoutLimits.maxExercisesPerDay} exercises per day, at least 1")
    appendLine(
        "- sets ${WorkoutLimits.sets.first}-${WorkoutLimits.sets.last}; " +
            "repetitions ${WorkoutLimits.reps.first}-${WorkoutLimits.reps.last}; " +
            "duration_seconds ${WorkoutLimits.durationSeconds.first}-${WorkoutLimits.durationSeconds.last}; " +
            "rest_seconds ${WorkoutLimits.restSeconds.first}-${WorkoutLimits.restSeconds.last}; " +
            "weight_kg ${WorkoutLimits.weightKg.start}-${WorkoutLimits.weightKg.endInclusive}",
    )
    appendLine(
        "- give exactly one of repetitions or duration_seconds, never both and never " +
            "neither: catalog rows marked R take repetitions, rows marked T take " +
            "duration_seconds. Set the other one to null.",
    )
    appendLine("- repetitions is one exact integer, never a range and never a text value")
    appendLine(
        "- weight_kg: a working weight suited to this person's experience and goal for " +
            "barbell, dumbbell, cable and machine work; null for body weight and for " +
            "anything that carries no load",
    )
    appendLine("- exercise_id must be copied exactly from the catalog; no other exercise exists")
    appendLine("- focus_muscles: the target_muscle values of the rows you used that day")
    appendLine("- rationale: one short paragraph explaining the week you built")
    appendLine()

    retryFeedback?.let {
        appendLine(it.toPromptSection())
        appendLine()
    }

    appendLine("CATALOG - the only exercises that exist (${candidates.size} rows)")
    appendLine(AiExerciseCandidate.CATALOG_HEADER)
    candidates.forEach { appendLine(it.toCatalogRow()) }
}.trim()

/**
 * The rejection, in words the model can act on.
 *
 * It used to be a JSON array of codes: `{"kind":"rule","code":"no_time_left"}`.
 * Those names are this codebase's, not anyone else's, and a model given one
 * could only guess what it had done. The mapping is authored here — a fixed
 * table from our own enum to our own sentence — so nothing the provider said
 * comes back to it as instruction, which was the reason codes were used in the
 * first place.
 */
private fun AiWorkoutRetryFeedback.toPromptSection(): String = buildString {
    appendLine("YOUR PREVIOUS ANSWER WAS REJECTED. Fix exactly these, then return the whole plan again:")
    issues.forEach { appendLine("- ${it.describe()}") }
}.trimEnd()
