package com.repforth.core.exercisedata

import androidx.annotation.StringRes
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.TrainingGoal

/**
 * Display names for [TrainingGoal].
 */
val TrainingGoal.labelRes: Int
    @StringRes get() = when (this) {
        TrainingGoal.STRENGTH -> R.string.goal_strength
        TrainingGoal.HYPERTROPHY -> R.string.goal_hypertrophy
        TrainingGoal.ENDURANCE -> R.string.goal_endurance
        TrainingGoal.GENERAL_FITNESS -> R.string.goal_general_fitness
    }

/**
 * Explanatory detail for [TrainingGoal].
 */
val TrainingGoal.detailRes: Int
    @StringRes get() = when (this) {
        TrainingGoal.STRENGTH -> R.string.goal_strength_detail
        TrainingGoal.HYPERTROPHY -> R.string.goal_hypertrophy_detail
        TrainingGoal.ENDURANCE -> R.string.goal_endurance_detail
        TrainingGoal.GENERAL_FITNESS -> R.string.goal_general_fitness_detail
    }

/**
 * Display names for [ExperienceLevel].
 *
 * The level, not the span of years. These read "Under a year", "1 to 3 years"
 * and "More than 3 years" until the maintainer asked for the shorter words: the
 * spans are the definition, not the name, and a definition makes a poor chip —
 * it is three times the width, it wraps, and it makes the reader do the
 * arithmetic to find out which end is which. The years moved to [detailRes],
 * where onboarding still shows them at the moment the question is asked.
 */
val ExperienceLevel.labelRes: Int
    @StringRes get() = when (this) {
        ExperienceLevel.BEGINNER -> R.string.experience_beginner
        ExperienceLevel.INTERMEDIATE -> R.string.experience_intermediate
        ExperienceLevel.ADVANCED -> R.string.experience_advanced
    }

/** How long that level means, for wherever there is room to say so. */
val ExperienceLevel.detailRes: Int
    @StringRes get() = when (this) {
        ExperienceLevel.BEGINNER -> R.string.experience_beginner_detail
        ExperienceLevel.INTERMEDIATE -> R.string.experience_intermediate_detail
        ExperienceLevel.ADVANCED -> R.string.experience_advanced_detail
    }
