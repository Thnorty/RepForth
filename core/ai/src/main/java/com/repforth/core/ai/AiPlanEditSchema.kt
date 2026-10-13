package com.repforth.core.ai

import com.repforth.core.model.WorkoutLimits
import kotlinx.serialization.json.JsonObject

/**
 * The JSON Schema for a plan edit, built from the same shapes as
 * [AiWorkoutJsonSchema] and under the same rule: shape only, no bounds, no
 * `enum`. `op` is a plain string for that reason; the prompt lists its values
 * and the strict codec refuses any other.
 *
 * `tools/gemini-plan-edit-schema.json` is a dump of this, kept current by
 * `SchemaDumpGuardTest`, so the Gemini probe scripts can be pointed at it.
 */
object AiPlanEditJsonSchema {
    val value: JsonObject = objectSchema(
        properties = linkedMapOf(
            "changes" to arraySchema(changeSchema()),
            "rationale" to stringSchema(),
        ),
    )

    private fun changeSchema() = objectSchema(
        properties = linkedMapOf(
            "op" to stringSchema(),
            "day" to integerSchema(),
            "position" to integerSchema(),
            "exercise_id" to nullable("string"),
            "sets" to nullable("integer"),
            "repetitions" to nullable("integer"),
            "duration_seconds" to nullable("integer"),
            "weight_kg" to nullable("number"),
            "rest_seconds" to nullable("integer"),
        ),
    )
}

/**
 * The plan-edit prompt, in the same headed-section form as generation's.
 *
 * Two things here are not in the generation prompt, and both exist because the
 * answer is a list of changes rather than a plan:
 *
 * - **Addresses.** Every exercise in CURRENT PLAN carries `day.position`, and the
 *   model is told positions mean the plan as sent. Without that, "remove 1.1,
 *   then add after 1.2" has two readings and the app would pick one silently.
 * - **Restraint.** A model asked to improve a plan will happily retune every set
 *   in it. The person asked for one thing; the prompt says to change only that,
 *   and the change list makes anything else visible in the review.
 *
 * The time and number sections are the shared ones, so this prompt and the
 * generation prompt cannot describe the same check two ways.
 */
internal fun AiPlanEditRequest.toPlanEditPrompt(
    retryFeedback: AiWorkoutRetryFeedback? = null,
): String = buildString {
    appendLine(
        "You are changing one person's existing training plan, as they ask, and nothing " +
            "else. Answer with a list of changes, not a new plan.",
    )
    appendLine()

    appendLine("BRIEF")
    appendLine("- what the person asked, in their words: $instruction")
    appendLine("- goal: $goal")
    appendLine("- training experience: $experience")
    appendLine("- time available per day: $sessionDurationMinutes minutes")
    appendLine("- write rationale in this language: $locale")
    appendLine()

    appendLine("DAYS")
    dayTitles.forEachIndexed { index, title -> appendLine("DAY ${index + 1}: $title") }
    appendLine()

    appendLine("CURRENT PLAN - ref is day.position, both counted from 1")
    appendLine(AiPlanRow.PLAN_HEADER)
    rows.forEach { appendLine(it.toRow()) }
    appendLine()

    focus?.let {
        appendLine(
            "FOCUS: ${it.day}.${it.position} - the person pointed at this exercise; " +
                "\"this exercise\" in their words means it",
        )
        appendLine()
    }

    appendLine("HOW TO ANSWER")
    appendLine(
        "- changes: at most ${PlanEditLimits.maxChanges} changes, each with an op, a day " +
            "and a position",
    )
    appendLine(
        "- replace: put a catalog exercise in place of the one at day.position. Give " +
            "exercise_id, sets, rest_seconds and one of repetitions or duration_seconds",
    )
    appendLine(
        "- add inserts after day.position, where 0 means the start of the day. Give the " +
            "same fields as replace. Several adds after one position appear in the order you list them",
    )
    appendLine("- remove: take out the exercise at day.position. Set every other field to null")
    appendLine(
        "- adjust: change the numbers of the exercise at day.position. Leave exercise_id " +
            "null, give only the numbers that change and set the rest to null. Keep its " +
            "measure: a repetition exercise stays repetitions, a timed one stays duration_seconds",
    )
    appendLine(
        "- day and position always refer to CURRENT PLAN exactly as shown above, never to " +
            "the plan after your other changes",
    )
    appendLine("- one replace, remove or adjust per exercise at most")
    appendLine(
        "- change only what the person asked for. Every exercise you do not name stays " +
            "exactly as it is",
    )
    appendLine("- never leave a day empty, and never add or remove whole days")
    appendLine(
        "- to swap an exercise for one that works the same muscles, pick a catalog row " +
            "whose target_muscle matches the original's",
    )
    appendLine(
        "- if nothing should change, return an empty changes list and say why in rationale",
    )
    appendLine("- rationale: one or two sentences on what you changed and why")
    appendLine()

    appendLine("TIME - this person usually trains $sessionDurationMinutes minutes a day")
    appendDurationFormula()
    appendLine(
        "- a day may run past ${sessionDurationMinutes * 60} seconds when that is what " +
            "the person asked for: adding exercises means adding them, not swapping " +
            "others out. Do not trim exercises they did not mention to make room. The " +
            "app shows them the new length before they accept",
    )
    appendLine()

    appendLine("NUMBERS - checked on arrival for every exercise you add, replace or adjust")
    appendNumberLimits()
    appendLine(
        "- a day holds at most ${WorkoutLimits.maxExercisesPerDay} exercises, and the " +
            "same exercise never twice in one day",
    )
    appendLine()

    retryFeedback?.let { feedback ->
        appendLine(
            "YOUR PREVIOUS ANSWER WAS REJECTED. Fix exactly these, then return the whole " +
                "list of changes again:",
        )
        feedback.issues.forEach { appendLine("- ${it.describe()}") }
        appendLine()
    }

    appendLine(
        "CATALOG - the only exercises you may add or swap in " +
            "(${candidates.size} ${if (candidates.size == 1) "row" else "rows"})",
    )
    appendLine(AiExerciseCandidate.CATALOG_HEADER)
    candidates.forEach { appendLine(it.toCatalogRow()) }
}.trim()
