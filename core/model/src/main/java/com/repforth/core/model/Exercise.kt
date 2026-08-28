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
    val attribution: String,
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

/*
 * Categorical values are normalised slugs rather than enums — for now.
 *
 * The guideline calls for enums or lookup tables, and that is the right end
 * state. But the vocabulary is the upstream dataset's, and it has not been
 * imported yet; inventing constants here would mean guessing at values the
 * importer will later contradict. The import task pins the real vocabulary, and
 * these become enums in the same change, with a data test asserting that every
 * value in the dataset maps to a constant.
 */

/** Coarse region, e.g. the value behind "chest". */
@JvmInline
value class BodyPart(val slug: String)

/** A specific muscle, used for both `target` and `secondaryMuscles`. */
@JvmInline
value class Muscle(val slug: String)

/** Required equipment; the empty-equipment case is a normal value, not null. */
@JvmInline
value class Equipment(val slug: String)
