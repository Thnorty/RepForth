package com.repforth.core.exercisedata

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Equipment
import com.repforth.core.model.Muscle

/**
 * Everything the catalog can be narrowed by (§3).
 *
 * One value object rather than a parameter list, so adding a facet later is a
 * field here instead of a new overload on every layer between the screen and
 * SQLite.
 *
 * [query] matches the exercise name, which the dataset ships only in English —
 * it translates instructions into ten languages but has a single `name` per
 * record. A Turkish user therefore searches English names. That is upstream's
 * text, not this project's, so §13 is not violated, but it is worth knowing
 * before anyone reports it as a bug.
 */
data class CatalogFilter(
    val query: String = "",
    val bodyPart: BodyPart? = null,
    val equipment: Equipment? = null,
    /**
     * Matches whether a muscle is the target, the muscle group, or a secondary
     * muscle. Empty means "no muscle constraint", not "match nothing".
     *
     * Synonyms are expanded before the query runs, so selecting `quads` also
     * finds records upstream labelled `quadriceps`.
     */
    val muscles: Set<Muscle> = emptySet(),
) {
    val isEmpty: Boolean
        get() = query.isBlank() && bodyPart == null && equipment == null && muscles.isEmpty()

    /**
     * The muscle set as upstream slugs, widened to cover every synonym.
     *
     * Selecting one member of a synonym pair has to match both, because the
     * dataset uses `abs` in one field and `abdominals` in another for the same
     * muscle. Canonicalising at query time keeps storage faithful (§6) while
     * making the filter behave the way a user expects.
     */
    fun muscleSlugs(): List<String> {
        if (muscles.isEmpty()) return emptyList()
        val wanted = muscles.mapTo(mutableSetOf()) { it.canonical }
        return Muscle.entries.filter { it.canonical in wanted }.map { it.slug }
    }
}
