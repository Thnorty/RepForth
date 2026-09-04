package com.repforth.app

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import com.repforth.core.exercisedata.R as TermsR
import com.repforth.feature.builder.R as BuilderR
import com.repforth.feature.onboarding.R as OnboardingR
import com.repforth.feature.session.R as SessionR
import com.repforth.feature.settings.R as SettingsR

/**
 * Every word these tests look for on screen, named by the resource that draws it.
 *
 * A test that types `onNodeWithText("Save workout")` keeps a second copy of a
 * user-visible string, and the repo's one rule says a second copy is a bug. It
 * behaves like one: the experience chip was renamed from "1 to 3 years" to
 * "Intermediate" and every test in this module failed on a walk that still
 * tapped the old words. Nothing in the build could have said so, because to the
 * compiler a string literal in a test is just a string.
 *
 * Read through `R` instead and the two failure modes separate properly. Delete
 * or rename the resource and this file stops compiling. Reword it and the tests
 * follow, because "the save button" is what they meant — its wording is the
 * screen's business, and asserting on it here only ever produced false
 * failures.
 *
 * `AppRobot` used to carry a note saying this could not be done: a module's
 * `implementation` dependencies reach `androidTest` at runtime but not at
 * compile time, so `BuilderR` genuinely did not resolve. That much was right.
 * The fix was three lines up its own build file, where `:core:model` and
 * `:feature:session` were already re-declared as `androidTestImplementation`
 * for the same reason.
 *
 * It also makes a non-English run possible. The device is `en-US` today and the
 * old note argued a Turkish device failing loudly was the failure worth having;
 * passing is better, and §Localisation calls both languages first-class.
 */
internal object AppText {

    // The four bottom-bar tabs. `catalog`, not `exercises` -- the id predates
    // the label and `:app` owns the shell's own vocabulary.
    val today: String get() = text(R.string.today)
    val plans: String get() = text(R.string.plans)
    val exercises: String get() = text(R.string.catalog)
    val progress: String get() = text(R.string.progress)

    // Top-bar icons, which carry a description and no text.
    val settings: String get() = text(R.string.nav_settings)
    val back: String get() = text(R.string.nav_back)

    /** The Plans FAB, also by description: Material3 clears its inner semantics. */
    val newWorkout: String get() = text(BuilderR.string.plans_new)

    // Onboarding. The goal and experience chips are drawn from the catalog's
    // vocabulary rather than the feature's, through `ProfileTerms`.
    val goalStrength: String get() = text(TermsR.string.goal_strength)
    val experienceIntermediate: String get() = text(TermsR.string.experience_intermediate)
    val onboardingNext: String get() = text(OnboardingR.string.onboarding_next)
    val onboardingFinish: String get() = text(OnboardingR.string.onboarding_finish)

    /** The questionnaire's first step, and so the marker for "onboarding is up". */
    val onboardingGoalTitle: String get() = text(OnboardingR.string.onboarding_goal_title)

    // The builder, its picker, and the Coach entry points on it.
    val addExercise: String get() = text(BuilderR.string.builder_add_exercise)

    /** The picker's search field, and so the marker for "the picker is up". */
    val pickSearch: String get() = text(BuilderR.string.builder_pick_search)

    /** Shown once the picker has searched and found nothing. */
    val pickEmpty: String get() = text(BuilderR.string.builder_pick_empty)

    /** The picker's own close button, which is now the way back to the builder. */
    val pickClose: String get() = text(BuilderR.string.builder_pick_close)
    val addToWorkout: String get() = text(BuilderR.string.builder_add_to_workout)
    val saveWorkout: String get() = text(BuilderR.string.builder_save)
    val sets: String get() = text(BuilderR.string.builder_sets)
    val coachOpen: String get() = text(BuilderR.string.coach_open)
    val coachGenerate: String get() = text(BuilderR.string.coach_generate)
    val discard: String get() = text(BuilderR.string.builder_discard_dialog_confirm)

    /** The short label on a plan row's own button, not Today's "Start workout". */
    val startPlan: String get() = text(BuilderR.string.plans_start_short)

    val conflictTitle: String get() = text(SessionR.string.session_conflict_title)
    val appearance: String get() = text(SettingsR.string.settings_appearance)

    /**
     * The app's resources, not the test APK's.
     *
     * Read per call rather than cached in a `val`: the object would otherwise
     * initialise on whatever configuration the first test happened to run
     * under, and hold it for the rest of the process.
     */
    private fun text(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
