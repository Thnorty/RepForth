package com.repforth.feature.onboarding

import androidx.annotation.StringRes
import com.repforth.core.exercisedata.detailRes
import com.repforth.core.exercisedata.labelRes

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
        OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
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
        OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_subtitle
        OnboardingStep.REVIEW -> R.string.onboarding_review_subtitle
    }
