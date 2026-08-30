package com.repforth.core.model

/**
 * A saved plan (§7).
 *
 * [source] records how it came to exist. §8 requires a generated plan to arrive
 * as editable workout cards rather than a chat transcript, so the moment a user
 * edits one it stops being faithful to what was generated — keeping the origin
 * lets the app say where a plan came from without claiming its current contents
 * are still that.
 */
data class WorkoutTemplate(
    val id: String,
    val name: String,
    val notes: String? = null,
    val source: PlanSource,
    val exercises: List<PlannedExercise>,
) {
    init {
        require(name.isNotBlank()) { "A plan needs a name" }
        require(exercises.map { it.position } == exercises.indices.toList()) {
            "Plan positions must be contiguous from zero, in order"
        }
    }

    /**
     * Roughly how long this will take, for the session-length ceiling (§3).
     *
     * An estimate, and honest about it: the rest between sets is known exactly,
     * the work is not. See [PlannedExercise.estimatedDurationMs].
     */
    val estimatedDurationMs: Long
        get() = exercises.sumOf { it.estimatedDurationMs }
}

enum class PlanSource {
    /** Built by hand in the workout builder. */
    MANUAL,

    /** Legacy origin retained so plans saved before local generation was removed still load. */
    RULES,

    /** Produced from an AI request, then validated against the same rules. */
    AI,
}

/** One exercise in a plan, with its targets. */
data class PlannedExercise(
    val id: String,
    val exerciseId: ExerciseId,
    val position: Int,
    val target: ExerciseTarget,
    val restMs: Long,
) {
    init {
        require(position >= 0) { "position must not be negative" }
        require(restMs >= 0) { "rest must not be negative" }
    }

    /**
     * Work plus rest, estimated.
     *
     * Rest is exact. Work is not: a rep takes as long as it takes, and the
     * dataset says nothing about tempo. [WorkoutLimits.secondsPerRepEstimate] is
     * a stated assumption rather than a measurement, chosen so a set of ten
     * lands near half a minute, which is the common case. It exists to keep a
     * plan inside a session-length ceiling, not to promise a finish time.
     *
     * The last set's rest still counts. In practice a user does rest after their
     * final set before moving on, and leaving it out makes long plans look
     * shorter than they are — which is the failure that matters here.
     */
    val estimatedDurationMs: Long
        get() {
            val workPerSet = when (val t = target) {
                is ExerciseTarget.Reps ->
                    t.reps * WorkoutLimits.secondsPerRepEstimate * 1000L
                is ExerciseTarget.Duration -> t.durationMs
            }
            return target.sets * (workPerSet + restMs)
        }
}

/**
 * What a set is measured in.
 *
 * A sealed type rather than two nullable columns' worth of nullable fields: a
 * plank has a duration and no reps, a curl has reps and no duration, and there is
 * no such thing as both or neither. Modelling it this way means the impossible
 * states cannot be constructed, so nothing downstream has to check for them.
 */
sealed interface ExerciseTarget {
    val sets: Int

    /** Kilograms, always. Null for bodyweight, or when the user is not tracking load. */
    val weightKg: Double?

    data class Reps(
        override val sets: Int,
        val reps: Int,
        override val weightKg: Double? = null,
    ) : ExerciseTarget {
        init {
            require(sets > 0) { "A target needs at least one set" }
            require(reps > 0) { "A rep target needs at least one rep" }
            require(weightKg == null || weightKg >= 0) { "Weight cannot be negative" }
        }
    }

    data class Duration(
        override val sets: Int,
        val durationMs: Long,
        override val weightKg: Double? = null,
    ) : ExerciseTarget {
        init {
            require(sets > 0) { "A target needs at least one set" }
            require(durationMs > 0) { "A timed target needs a positive duration" }
            require(weightKg == null || weightKg >= 0) { "Weight cannot be negative" }
        }
    }
}
