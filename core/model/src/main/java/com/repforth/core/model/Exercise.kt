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
    val attribution: String = "© Gym visual — https://gymvisual.com/",
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

/**
 * What a catalog list row needs, and nothing more.
 *
 * Loading full [Exercise] objects for a list would pull both languages of every
 * instruction step — 15,420 rows to render 1,324 names. The detail screen loads
 * the whole thing; a list does not need it.
 *
 * [name] is not localised, and cannot be: the dataset translates instructions
 * into ten languages but ships a single English `name` per record (§13 applies
 * to text this project authors, and this text is upstream's).
 */
data class ExerciseSummary(
    val id: ExerciseId,
    val name: String,
    val bodyPart: BodyPart,
    val target: Muscle,
    val equipment: Equipment,
    val thumbnail: MediaRef = MediaRef.Unavailable,
)
