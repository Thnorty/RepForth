package com.repforth.core.model

/*
 * Categorical vocabulary, derived from the pinned dataset (§6).
 *
 * These were slug value classes until the dataset was actually read; the
 * constants below are the values it uses, not values anyone guessed. The upstream
 * string travels with each constant so the original can always be recovered, and
 * `dataset-vocabulary.json` records what the dataset contained at the pinned
 * commit. `CategoricalVocabularyTest` fails if a value appears there with no
 * constant here, which is what turns a dataset bump into a build failure rather
 * than a silent "unknown" in the catalog.
 */

/**
 * Coarse region. This is the one categorical field upstream actually enumerates
 * in its own JSON schema, so it is the dataset's contract rather than an
 * observation of its current contents.
 */
enum class BodyPart(val slug: String) {
    BACK("back"),
    CARDIO("cardio"),
    CHEST("chest"),
    LOWER_ARMS("lower arms"),
    LOWER_LEGS("lower legs"),
    NECK("neck"),
    SHOULDERS("shoulders"),
    UPPER_ARMS("upper arms"),
    UPPER_LEGS("upper legs"),
    WAIST("waist"),
    ;

    companion object {
        fun fromSlug(slug: String): BodyPart? = entries.firstOrNull { it.slug == slug }
    }
}

/** Required equipment. `BODY_WEIGHT` is the largest single group, not an absence. */
enum class Equipment(val slug: String) {
    ASSISTED("assisted"),
    BAND("band"),
    BARBELL("barbell"),
    BODY_WEIGHT("body weight"),
    BOSU_BALL("bosu ball"),
    CABLE("cable"),
    DUMBBELL("dumbbell"),
    ELLIPTICAL_MACHINE("elliptical machine"),
    EZ_BARBELL("ez barbell"),
    HAMMER("hammer"),
    KETTLEBELL("kettlebell"),
    LEVERAGE_MACHINE("leverage machine"),
    MEDICINE_BALL("medicine ball"),
    OLYMPIC_BARBELL("olympic barbell"),
    RESISTANCE_BAND("resistance band"),
    ROLLER("roller"),
    ROPE("rope"),
    SKIERG_MACHINE("skierg machine"),
    SLED_MACHINE("sled machine"),
    SMITH_MACHINE("smith machine"),
    STABILITY_BALL("stability ball"),
    STATIONARY_BIKE("stationary bike"),
    STEPMILL_MACHINE("stepmill machine"),
    TIRE("tire"),
    TRAP_BAR("trap bar"),
    UPPER_BODY_ERGOMETER("upper body ergometer"),
    WEIGHTED("weighted"),
    WHEEL_ROLLER("wheel roller"),
    ;

    companion object {
        fun fromSlug(slug: String): Equipment? = entries.firstOrNull { it.slug == slug }

        /**
         * The equipment worth asking about without being asked.
         *
         * Measured from the packaged catalog rather than guessed: each of these
         * accounts for at least 2% of the 1,324 exercises, and together they
         * cover 91%. The remaining eighteen share 9% between them, and eight of
         * those have a single exercise each — so a screen that lists all
         * twenty-eight as equals is mostly noise, and the profile it collects is
         * worse for it.
         *
         * A fact about the catalog rather than a layout choice, which is why it
         * lives beside the vocabulary and not in the screen that renders it.
         * `EquipmentCoverageTest` re-derives the share from the database, so a
         * dataset update that changes what is common fails rather than quietly
         * leaving this stale.
         */
        val COMMON: List<Equipment> = listOf(
            BODY_WEIGHT,
            DUMBBELL,
            CABLE,
            BARBELL,
            LEVERAGE_MACHINE,
            BAND,
            SMITH_MACHINE,
            KETTLEBELL,
            WEIGHTED,
            STABILITY_BALL,
        )

        /** Everything else, alphabetically. Reachable, but behind a disclosure. */
        val UNCOMMON: List<Equipment> = entries.filterNot { it in COMMON }.sortedBy { it.slug }
    }
}

/**
 * A muscle, as named by the dataset.
 *
 * One constant per distinct upstream string, deliberately. The dataset uses three
 * overlapping vocabularies -- `target`, `muscle_group` and `secondary_muscles` --
 * which disagree with each other: `abs` and `abdominals` are the same muscle, as
 * are `quads` and `quadriceps`. Collapsing them here would discard which word the
 * source used, and §6 forbids silently rewriting upstream values.
 *
 * [canonical] does the collapsing instead, so filtering can treat synonyms as one
 * muscle while storage keeps what upstream said. Only unambiguous same-muscle
 * pairs are merged; terms that are anatomically *nested* rather than synonymous
 * -- `lower abs` inside `abs`, `rear deltoids` inside `delts`, `soleus` inside the
 * calves -- stay separate, because merging those is a product decision about how
 * filters should behave and not a fact about the data.
 */
enum class Muscle(val slug: String) {
    ABDOMINALS("abdominals"),
    ABDUCTORS("abductors"),
    ABS("abs"),
    ADDUCTORS("adductors"),
    ANKLE_STABILIZERS("ankle stabilizers"),
    ANKLES("ankles"),
    BACK("back"),
    BICEPS("biceps"),
    BRACHIALIS("brachialis"),
    CALVES("calves"),
    CARDIOVASCULAR_SYSTEM("cardiovascular system"),
    CHEST("chest"),
    CORE("core"),
    DELTOIDS("deltoids"),
    DELTS("delts"),
    FEET("feet"),
    FOREARMS("forearms"),
    GLUTES("glutes"),
    GRIP_MUSCLES("grip muscles"),
    GROIN("groin"),
    HAMSTRINGS("hamstrings"),
    HANDS("hands"),
    HIP_FLEXORS("hip flexors"),
    INNER_THIGHS("inner thighs"),
    LATISSIMUS_DORSI("latissimus dorsi"),
    LATS("lats"),
    LEVATOR_SCAPULAE("levator scapulae"),
    LOWER_ABS("lower abs"),
    LOWER_BACK("lower back"),
    OBLIQUES("obliques"),
    PECTORALS("pectorals"),
    QUADRICEPS("quadriceps"),
    QUADS("quads"),
    REAR_DELTOIDS("rear deltoids"),
    RHOMBOIDS("rhomboids"),
    ROTATOR_CUFF("rotator cuff"),
    SERRATUS_ANTERIOR("serratus anterior"),
    SHINS("shins"),
    SHOULDERS("shoulders"),
    SOLEUS("soleus"),
    SPINE("spine"),
    STERNOCLEIDOMASTOID("sternocleidomastoid"),
    TRAPEZIUS("trapezius"),
    TRAPS("traps"),
    TRICEPS("triceps"),
    UPPER_BACK("upper back"),
    UPPER_CHEST("upper chest"),
    WRIST_EXTENSORS("wrist extensors"),
    WRIST_FLEXORS("wrist flexors"),
    WRISTS("wrists"),
    ;

    /**
     * The muscle this one is a synonym of, or itself.
     *
     * Filtering should compare canonical values; a user asking for quads means
     * both `quads` and `quadriceps`.
     */
    val canonical: Muscle
        get() = SYNONYMS[this] ?: this

    companion object {
        fun fromSlug(slug: String): Muscle? = entries.firstOrNull { it.slug == slug }

        /**
         * Same muscle, different word. Reviewed by hand -- every entry here is a
         * claim that two upstream terms are interchangeable, which no amount of
         * counting occurrences can establish on its own.
         */
        private val SYNONYMS: Map<Muscle, Muscle> = mapOf(
            ABDOMINALS to ABS,
            QUADRICEPS to QUADS,
            LATISSIMUS_DORSI to LATS,
            TRAPEZIUS to TRAPS,
            DELTOIDS to DELTS,
            SHOULDERS to DELTS,
            CHEST to PECTORALS,
            INNER_THIGHS to ADDUCTORS,
            ANKLE_STABILIZERS to ANKLES,
        )
    }
}
