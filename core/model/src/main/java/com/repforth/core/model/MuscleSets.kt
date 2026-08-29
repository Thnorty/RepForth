package com.repforth.core.model

/**
 * Selecting muscles, in one place.
 *
 * The rule that a muscle is really its whole synonym group was implemented four
 * times — twice in feature ViewModels, once in the catalog filter, once in
 * [UserProfile] — each spelling out `Muscle.entries.filter { it.canonical == … }`
 * again. Four copies of one rule is four chances for a selection to be written
 * half-applied, which the rules engine would then honour half of.
 *
 * It belongs beside the vocabulary because it is a fact about the vocabulary:
 * the upstream dataset names the same muscle more than once, and every consumer
 * has to account for that identically.
 */

/**
 * Every upstream name for this muscle, including itself.
 *
 * `abs` and `abdominals` are one muscle under two labels. Selecting one and
 * leaving the other produces a filter that misses rows and a preference the
 * rules engine only partly obeys.
 */
val Muscle.synonyms: Set<Muscle>
    get() = Muscle.entries.filterTo(mutableSetOf()) { it.canonical == canonical }

/** The canonical muscles, one per real muscle rather than one per upstream name. */
val CanonicalMuscles: List<Muscle> =
    Muscle.entries.filter { it.canonical == it }.sortedBy { it.slug }

/**
 * Adds or removes a muscle, taking its synonyms with it.
 *
 * All-or-nothing on the group: a set containing part of a group would render as
 * a chip that will not turn off, because the thing being toggled is not the
 * thing being displayed.
 */
fun Set<Muscle>.toggleSynonyms(muscle: Muscle): Set<Muscle> =
    toggleAll(muscle.synonyms)

/**
 * Adds or removes every muscle in a region at once.
 *
 * One action, not one per muscle. Toggling each individually leaves a region
 * half-selected whenever some of its muscles were already chosen, which reads
 * on the body map as a region that will not turn off.
 */
fun Set<Muscle>.toggleRegion(region: BodyRegion): Set<Muscle> =
    toggleAll(region.muscles.flatMapTo(mutableSetOf()) { it.synonyms })

/** Every synonym of every muscle in the region — what [toggleRegion] acts on. */
fun BodyRegion.allMuscles(): Set<Muscle> =
    muscles.flatMapTo(mutableSetOf()) { it.synonyms }

private fun Set<Muscle>.toggleAll(group: Set<Muscle>): Set<Muscle> =
    if (containsAll(group)) this - group else this + group
