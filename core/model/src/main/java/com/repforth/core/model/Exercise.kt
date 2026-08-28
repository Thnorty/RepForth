package com.repforth.core.model

/**
 * The catalog exercise, as the rest of the app sees it (§6).
 *
 * This is a plain domain type: no Room annotations, no JSON annotations, no
 * Android imports. Storage and transport shapes map onto it, never the other
 * way round — so changing a column name cannot ripple into feature code.
 */
data class Exercise(
    val id: ExerciseId,
    val name: String,
    val bodyPart: BodyPart,
    val target: Muscle,
    val muscleGroup: Muscle,
    val secondaryMuscles: Set<Muscle>,
    val equipment: Equipment,
    val instructions: LocalizedInstructions,
    val thumbnail: MediaRef,
    val animation: MediaRef,
)

/**
 * The upstream dataset's stable identifier (§6: "never silently change upstream
 * IDs"). A value class, so an exercise ID can never be passed where a plan ID or
 * a raw name is expected.
 */
@JvmInline
value class ExerciseId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExerciseId must not be blank" }
    }
}
