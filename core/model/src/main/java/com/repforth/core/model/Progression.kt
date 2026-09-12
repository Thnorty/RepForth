package com.repforth.core.model

/**
 * What "that was easy" does to the plan that made it easy.
 *
 * §3 asks the user how hard a workout was and stores the answer. Until now that
 * was the whole of it: the number went to history and nothing read it back, so
 * the one question the app asks about how training is going changed nothing
 * about the training. This is the other half — the answer moves the saved plan,
 * once, on the next workout, if the user accepts it.
 *
 * ## The rule, entire
 *
 * **A level on a load is the smaller of [WorkoutLimits.weightKgStep] and a tenth
 * of it. A level on repetitions is one. A level on a timed set is
 * [DURATION_LEVEL_SECONDS].** "A little easy" moves one level and "too easy"
 * moves two, and the same going down.
 *
 * The two halves of the load rule each exist for a case the other gets wrong,
 * and one of them always binds:
 *
 * - **The tenth** is what makes a light dumbbell behave. Five kilograms on a ten
 *   kilogram lateral raise is half again in one session, which is not
 *   progression, it is a different exercise.
 * - **The five kilogram ceiling** is what stops a heavy lift running away. A
 *   tenth of a hundred and twenty kilogram squat is twelve kilograms after one
 *   good session, which nobody would write into a plan by hand.
 *
 * ## Why a plan carries two numbers for one weight
 *
 * A tenth of ten kilograms is one kilogram, and the plan is written in fives, so
 * the honest answer to "add a level" is a weight that does not exist on the
 * grid. [PlannedExercise.progressKg] is where the remainder lives: the plan
 * shows a weight a rack can hold and remembers how far past it the user has
 * crept, and the two are added back together only here.
 *
 * So three easy sessions walk a ten kilogram dumbbell 10.0, 11.0, 12.1, 13.31 —
 * showing 10, 10, 10, 15. The number on screen moves when the truth underneath
 * has actually earned it, and never by half a session's work.
 *
 * **Both numbers are honest on their own**, which is why it is two and not one
 * unrounded weight rounded at the reading end. The builder's weight field is
 * where a load is *stated*, so it has to show what is stored; a field that
 * displays a rounded copy of a number it is editing rewrites itself under the
 * cursor on every recomposition. Storing the shown weight keeps every screen,
 * the builder included, reading the same field it always did.
 *
 * ## What this deliberately does not do
 *
 * **It never applies itself.** These are pure functions over a plan; the screen
 * offers the result and writes it only if the user says so.
 *
 * **Repetitions and seconds keep no remainder.** One repetition out of ten and
 * ten seconds out of forty-five are already fine steps against the thing they
 * measure. Only kilograms have a grid coarse enough to need a carry, and a
 * second accumulator that never accumulated anything would be a field to keep
 * correct for nothing.
 *
 * **An exercise with no load gains repetitions forever.** A real programme caps
 * the repetitions and converts to weight; this has nothing to convert to, since
 * a press-up has no dumbbell to reach for. Known, and left.
 */

/** A level on a timed set. Five seconds is the grid; ten is the move. */
const val DURATION_LEVEL_SECONDS = 10

/**
 * How much of a load one level is, as a fraction.
 *
 * Private because it is never the whole answer: [loadLevelKg] is, and reading
 * the fraction without the ceiling beside it is how a heavy lift gains twelve
 * kilograms in a session.
 */
private const val LOAD_LEVEL_FRACTION = 0.10

/**
 * How many levels each answer to §3's question moves the plan, signed.
 *
 * The scale runs 1 "too easy" to 5 "too hard", so an easy workout is a *low*
 * number and a positive move. Symmetric on purpose: nothing about being two
 * levels too hard is less true than being two levels too easy.
 *
 * "Just right" is zero, and so is an unanswered question — the middle of the
 * scale is the answer a well-judged session lands on, and the whole point of
 * asking was to find out when it did not.
 */
fun progressionLevels(effort: Int?): Int = when (effort) {
    1 -> 2
    2 -> 1
    4 -> -1
    5 -> -2
    else -> 0
}

/** One level of a load, in kilograms. See the file comment for both halves. */
fun loadLevelKg(loadKg: Double): Double =
    minOf(WorkoutLimits.weightKgStep, loadKg * LOAD_LEVEL_FRACTION)

/**
 * The plan as it would read after [levels], leaving this one untouched.
 *
 * Zero returns the receiver rather than a copy, so "Just right" and an
 * unanswered question cost nothing and cannot accidentally rewrite a plan with
 * identical contents and a new updated-at.
 */
fun WorkoutTemplate.progressedBy(levels: Int): WorkoutTemplate =
    if (levels == 0) this else copy(exercises = exercises.map { it.progressedBy(levels) })

/**
 * One exercise's share of a progression, before and after.
 *
 * [isVisible] is the distinction the finish screen is built around: a light load
 * can move underneath without the plan reading any differently, and a row that
 * said "10 kg becomes 10 kg" would look broken rather than patient. The screen
 * lists the visible ones and says the rest in a sentence.
 */
data class ProgressionChange(
    val exerciseId: ExerciseId,
    val before: ExerciseTarget,
    val after: ExerciseTarget,
    /** Positive when the exercise is getting harder, whatever moved. */
    val levels: Int,
    /** The exact load moved even though the plan will read the same. */
    val movesUnderneath: Boolean,
) {
    /** Whether the plan will read differently afterwards. */
    val isVisible: Boolean get() = before != after

    /**
     * Whether anything happened at all.
     *
     * False for an exercise already against a limit -- a five kilogram load told
     * to get lighter, a single repetition told to get shorter. Nothing moves,
     * nothing is owed, and the screen should not claim otherwise.
     */
    val moves: Boolean get() = isVisible || movesUnderneath

    /**
     * The weight this load is creeping toward, one grid step along.
     *
     * Null when there is no load, or when the next step would leave what a plan
     * can hold. It is the honest end of "stays at 10 kg and moves closer to…",
     * and the only reason the sentence can name a number at all.
     */
    val creepingToward: Double?
        get() {
            val shown = after.weightKg ?: return null
            val next = shown + if (levels > 0) WorkoutLimits.weightKgStep else -WorkoutLimits.weightKgStep
            return next.takeIf { it in WorkoutLimits.weightKgStep..WorkoutLimits.weightKg.endInclusive }
        }
}

/** What [progressedBy] would do, one entry per exercise, in plan order. */
fun WorkoutTemplate.progressionPreview(levels: Int): List<ProgressionChange> =
    exercises.map { planned ->
        val after = planned.progressedBy(levels)
        ProgressionChange(
            exerciseId = planned.exerciseId,
            before = planned.target,
            after = after.target,
            levels = levels,
            movesUnderneath = after.progressKg != planned.progressKg,
        )
    }

/**
 * One exercise, moved by [levels].
 *
 * **A load moves if there is one, whatever the exercise is measured in.** A
 * weighted plank gets heavier rather than longer, because adding seconds to a
 * loaded hold changes what is being trained and adding plate does not. Only an
 * exercise with nothing on it moves its repetitions or its clock.
 */
fun PlannedExercise.progressedBy(levels: Int): PlannedExercise {
    if (levels == 0) return this
    val load = target.weightKg
    if (load != null && load > 0.0) {
        val exact = load + progressKg
        val moved = (exact + levels * loadLevelKg(exact))
            // The floor is one step rather than zero. Below a step the plan can
            // only show a step anyway, and letting the exact load drift down to
            // a fraction of a kilogram would take a dozen easy sessions to climb
            // back out of.
            .coerceIn(WorkoutLimits.weightKgStep, WorkoutLimits.weightKg.endInclusive)
        val shown = WorkoutLimits.roundWeightKg(moved)
        return copy(target = target.withWeightKg(shown), progressKg = moved - shown)
    }
    return when (val current = target) {
        is ExerciseTarget.Reps ->
            copy(target = current.copy(reps = (current.reps + levels).coerceIn(WorkoutLimits.reps)))

        is ExerciseTarget.Duration -> {
            val seconds = (current.durationMs / 1000L).toInt() + levels * DURATION_LEVEL_SECONDS
            copy(
                target = current.copy(
                    durationMs = seconds.coerceIn(WorkoutLimits.durationSeconds) * 1000L,
                ),
            )
        }
    }
}

/** The same target carrying a different load. */
private fun ExerciseTarget.withWeightKg(kg: Double): ExerciseTarget = when (this) {
    is ExerciseTarget.Reps -> copy(weightKg = kg)
    is ExerciseTarget.Duration -> copy(weightKg = kg)
}
