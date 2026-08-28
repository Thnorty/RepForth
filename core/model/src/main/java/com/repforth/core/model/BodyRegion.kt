package com.repforth.core.model

/**
 * Which side of the body a region is drawn on.
 *
 * The picker shows one view at a time and cross-fades; a region present in both
 * is highlighted in both, so turning the body around never makes a selection
 * appear to be lost.
 */
enum class BodyView {
    FRONT,
    BACK,
}

/**
 * A place on the body a user can point at.
 *
 * Deliberately coarser than [Muscle]. The dataset names 50 muscles and 41 remain
 * after synonyms merge, but a body drawn at picker size has nowhere to put most
 * of that: `wrist flexors`, `wrist extensors`, `grip muscles` and `hands` are
 * all the same few square millimetres of forearm. Nineteen regions is what can
 * actually be tapped.
 *
 * This is a lossy view *onto* the muscle vocabulary, never a replacement for it.
 * Selecting a region means selecting every muscle that maps to it, and the
 * filter still works in muscles — so a region can be redrawn or split without
 * touching stored data.
 */
enum class BodyRegion(val views: Set<BodyView>) {
    NECK(setOf(BodyView.FRONT, BodyView.BACK)),
    TRAPS(setOf(BodyView.BACK)),
    SHOULDERS(setOf(BodyView.FRONT, BodyView.BACK)),
    CHEST(setOf(BodyView.FRONT)),
    UPPER_BACK(setOf(BodyView.BACK)),
    LATS(setOf(BodyView.BACK)),
    LOWER_BACK(setOf(BodyView.BACK)),
    ABS(setOf(BodyView.FRONT)),
    OBLIQUES(setOf(BodyView.FRONT)),
    BICEPS(setOf(BodyView.FRONT)),
    TRICEPS(setOf(BodyView.BACK)),
    FOREARMS(setOf(BodyView.FRONT, BodyView.BACK)),
    HIP_FLEXORS(setOf(BodyView.FRONT)),
    GLUTES(setOf(BodyView.BACK)),
    QUADS(setOf(BodyView.FRONT)),
    HAMSTRINGS(setOf(BodyView.BACK)),
    ADDUCTORS(setOf(BodyView.FRONT)),
    ABDUCTORS(setOf(BodyView.FRONT, BodyView.BACK)),
    LOWER_LEGS(setOf(BodyView.FRONT, BodyView.BACK)),
    ;

    /** Matches the `id` on the corresponding path in the body-map artwork. */
    val svgId: String = name.lowercase().replace('_', '-')

    /** Every muscle that lives here. Selecting the region selects all of them. */
    val muscles: List<Muscle> get() = Muscle.entries.filter { it.region == this }

    companion object {
        fun forView(view: BodyView): List<BodyRegion> = entries.filter { view in it.views }
    }
}

/**
 * Where this muscle is, or `null` when it is not a place on a body.
 *
 * `cardiovascular system` is the only such value in the pinned dataset. It is a
 * real training target and must stay filterable, so it belongs beside the map as
 * a chip rather than being dropped or pinned to an arbitrary region.
 */
val Muscle.region: BodyRegion?
    get() = when (this) {
        Muscle.STERNOCLEIDOMASTOID, Muscle.LEVATOR_SCAPULAE -> BodyRegion.NECK

        Muscle.TRAPS, Muscle.TRAPEZIUS -> BodyRegion.TRAPS

        // Rotator cuff sits under the deltoid and cannot be drawn separately at
        // this size; rear delts are the back face of the same cap.
        Muscle.DELTS, Muscle.DELTOIDS, Muscle.SHOULDERS,
        Muscle.REAR_DELTOIDS, Muscle.ROTATOR_CUFF,
        -> BodyRegion.SHOULDERS

        Muscle.PECTORALS, Muscle.CHEST, Muscle.UPPER_CHEST -> BodyRegion.CHEST

        Muscle.UPPER_BACK, Muscle.RHOMBOIDS -> BodyRegion.UPPER_BACK

        // Bare `back` goes to lats: it is the region that reads as "back" on a
        // silhouette, and the muscle stays distinct in storage regardless.
        Muscle.LATS, Muscle.LATISSIMUS_DORSI, Muscle.BACK -> BodyRegion.LATS

        Muscle.LOWER_BACK, Muscle.SPINE -> BodyRegion.LOWER_BACK

        Muscle.ABS, Muscle.ABDOMINALS, Muscle.LOWER_ABS, Muscle.CORE -> BodyRegion.ABS

        // Serratus is drawn with the obliques: it is the visible ribcage flank.
        Muscle.OBLIQUES, Muscle.SERRATUS_ANTERIOR -> BodyRegion.OBLIQUES

        Muscle.BICEPS, Muscle.BRACHIALIS -> BodyRegion.BICEPS

        Muscle.TRICEPS -> BodyRegion.TRICEPS

        // Everything from the elbow down. Four upstream terms for one small area.
        Muscle.FOREARMS, Muscle.WRIST_EXTENSORS, Muscle.WRIST_FLEXORS,
        Muscle.WRISTS, Muscle.HANDS, Muscle.GRIP_MUSCLES,
        -> BodyRegion.FOREARMS

        Muscle.HIP_FLEXORS -> BodyRegion.HIP_FLEXORS

        Muscle.GLUTES -> BodyRegion.GLUTES

        Muscle.QUADS, Muscle.QUADRICEPS -> BodyRegion.QUADS

        Muscle.HAMSTRINGS -> BodyRegion.HAMSTRINGS

        Muscle.ADDUCTORS, Muscle.INNER_THIGHS, Muscle.GROIN -> BodyRegion.ADDUCTORS

        Muscle.ABDUCTORS -> BodyRegion.ABDUCTORS

        // Shins at the front, calves at the back, and the ankle and foot muscles
        // that are too small to separate at picker size.
        Muscle.CALVES, Muscle.SOLEUS, Muscle.SHINS,
        Muscle.ANKLES, Muscle.ANKLE_STABILIZERS, Muscle.FEET,
        -> BodyRegion.LOWER_LEGS

        Muscle.CARDIOVASCULAR_SYSTEM -> null
    }
