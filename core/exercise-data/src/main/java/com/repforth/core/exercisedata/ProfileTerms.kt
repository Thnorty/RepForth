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
 */
val ExperienceLevel.labelRes: Int
    @StringRes get() = when (this) {
        ExperienceLevel.BEGINNER -> R.string.experience_beginner
        ExperienceLevel.INTERMEDIATE -> R.string.experience_intermediate
        ExperienceLevel.ADVANCED -> R.string.experience_advanced
    }
