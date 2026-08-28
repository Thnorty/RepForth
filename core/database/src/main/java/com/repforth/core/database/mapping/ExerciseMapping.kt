package com.repforth.core.database.mapping

import com.repforth.core.database.dao.ExerciseWithDetails
import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Exercise
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.InstructionText
import com.repforth.core.model.Language
import com.repforth.core.model.LocalizedInstructions
import com.repforth.core.model.MediaRef
import com.repforth.core.model.Muscle

/**
 * Entity to domain. The only direction that exists — nothing outside this module
 * builds an entity, and nothing inside it returns one.
 *
 * [thumbnail] and [animation] are parameters rather than columns because media
 * lives in the manifest, not the catalog (§6). In a `placeholder` build the
 * caller passes [MediaRef.Unavailable] and the licensing boundary holds without
 * the database being involved.
 */
fun ExerciseWithDetails.toDomain(
    thumbnail: MediaRef = MediaRef.Unavailable,
    animation: MediaRef = MediaRef.Unavailable,
): Exercise = Exercise(
    id = ExerciseId(exercise.id),
    name = exercise.name,
    bodyPart = BodyPart(exercise.bodyPart),
    target = Muscle(exercise.target),
    muscleGroup = Muscle(exercise.muscleGroup),
    secondaryMuscles = secondaryMuscles.mapTo(mutableSetOf()) { Muscle(it.muscle) },
    equipment = Equipment(exercise.equipment),
    instructions = buildInstructions(),
    thumbnail = thumbnail,
    animation = animation,
    attribution = exercise.attribution,
)

private fun ExerciseWithDetails.buildInstructions(): LocalizedInstructions {
    val stepsByLanguage = steps
        .sortedBy { it.position }
        .groupBy { it.language }

    val byLanguage = instructions.mapNotNull { row ->
        // An unrecognised tag means the catalog holds a language this build does
        // not know about. Dropping it is correct: LocalizedInstructions then
        // fails loudly for the language that IS missing, which is the real fault.
        val language = Language.fromTag(row.language) ?: return@mapNotNull null
        language to InstructionText(
            summary = row.summary,
            steps = stepsByLanguage[row.language].orEmpty().map { it.text },
        )
    }.toMap()

    return LocalizedInstructions(byLanguage)
}
