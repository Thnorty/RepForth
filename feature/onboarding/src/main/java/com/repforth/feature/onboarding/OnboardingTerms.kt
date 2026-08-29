package com.repforth.feature.onboarding

import androidx.annotation.StringRes
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.TrainingGoal

/**
 * Display names for the two enums onboarding introduces.
 *
 * Mirrors `CatalogTerms.kt`, which does the same for the catalog vocabulary: the
 * enum stays a domain constant with no idea how it is written, and the mapping
 * to a translated string lives in exactly one place. When settings grows an
 * "edit profile" screen it uses this rather than writing the labels again.
 *
 * Exhaustive `when` on purpose — adding a goal will not compile until it has a
 * name in both languages.
 */
@get:StringRes
internal val TrainingGoal.labelRes: Int
    get() = when (this) {
        TrainingGoal.STRENGTH -> R.string.goal_strength
        TrainingGoal.HYPERTROPHY -> R.string.goal_hypertrophy
        TrainingGoal.ENDURANCE -> R.string.goal_endurance
        TrainingGoal.GENERAL_FITNESS -> R.string.goal_general_fitness
    }

/**
 * What choosing this goal will actually do to the programming.
 *
 * "Hypertrophy" is a word the app would otherwise use for months at someone who
 * does not know it, and the cost of guessing wrong here is every plan the rules
 * engine builds. The detail names the consequence rather than defining the term.
 */
@get:StringRes
internal val TrainingGoal.detailRes: Int
    get() = when (this) {
        TrainingGoal.STRENGTH -> R.string.goal_strength_detail
        TrainingGoal.HYPERTROPHY -> R.string.goal_hypertrophy_detail
        TrainingGoal.ENDURANCE -> R.string.goal_endurance_detail
        TrainingGoal.GENERAL_FITNESS -> R.string.goal_general_fitness_detail
    }

/**
 * Phrased as elapsed time rather than as a self-assessment.
 *
 * "Under a year" is a fact the user knows; "beginner" is a judgement they may
 * get wrong in either direction, and the answer feeds exercise complexity.
 */
@get:StringRes
internal val ExperienceLevel.labelRes: Int
    get() = when (this) {
        ExperienceLevel.BEGINNER -> R.string.experience_beginner
        ExperienceLevel.INTERMEDIATE -> R.string.experience_intermediate
        ExperienceLevel.ADVANCED -> R.string.experience_advanced
    }

/**
 * The question each step asks, and the reason it is being asked.
 *
 * Kept beside the step definition rather than inside the composable so that
 * adding a step forces both halves to be written: the enum constant will not
 * compile without a title and a subtitle, and neither will compile without a
 * string in both languages.
 */
@get:StringRes
internal val OnboardingStep.titleRes: Int
    get() = when (this) {
        OnboardingStep.GOAL -> R.string.onboarding_goal_title
        OnboardingStep.EXPERIENCE -> R.string.onboarding_experience_title
        OnboardingStep.EQUIPMENT -> R.string.onboarding_equipment_title
        OnboardingStep.DAYS -> R.string.onboarding_days_title
        OnboardingStep.LENGTH -> R.string.onboarding_length_title
        OnboardingStep.MUSCLES -> R.string.onboarding_muscles_title
        OnboardingStep.AVOID -> R.string.onboarding_avoid_title
        OnboardingStep.REVIEW -> R.string.onboarding_review_title
    }

@get:StringRes
internal val OnboardingStep.subtitleRes: Int
    get() = when (this) {
        OnboardingStep.GOAL -> R.string.onboarding_goal_subtitle
        OnboardingStep.EXPERIENCE -> R.string.onboarding_experience_subtitle
        OnboardingStep.EQUIPMENT -> R.string.onboarding_equipment_subtitle
        OnboardingStep.DAYS -> R.string.onboarding_days_subtitle
        OnboardingStep.LENGTH -> R.string.onboarding_length_subtitle
        OnboardingStep.MUSCLES -> R.string.onboarding_muscles_subtitle
        OnboardingStep.AVOID -> R.string.onboarding_avoid_subtitle
        OnboardingStep.REVIEW -> R.string.onboarding_review_subtitle
    }
