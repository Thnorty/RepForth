package com.repforth.feature.onboarding

import com.repforth.core.model.BodyRegion
import com.repforth.core.model.Equipment
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.Muscle
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.userdata.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The questionnaire's rules, tested without a database or a device.
 *
 * The parts worth testing are the ones a device would not reveal: that the two
 * questions with no default cannot be walked past, that a profile is only built
 * from a complete set of answers, and that minutes become milliseconds exactly
 * once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var profiles: RecordingProfileRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profiles = RecordingProfileRepository()
        viewModel = OnboardingViewModel(profiles)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun answerRequiredQuestions() {
        viewModel.onGoalSelected(TrainingGoal.STRENGTH)
        viewModel.onExperienceSelected(ExperienceLevel.INTERMEDIATE)
    }

    /**
     * Walks forward to a step, and gives up rather than spinning.
     *
     * The bound is not defensive padding. The unbounded version span forever the
     * first time a step legitimately refused to advance — a hung test task with
     * no output, which is a far worse thing to debug than a failed assertion.
     */
    private fun advanceTo(target: OnboardingStep) {
        answerRequiredQuestions()
        repeat(OnboardingStep.ordered.size) {
            if (state.step == target) return
            viewModel.onNext()
        }
        assertEquals("Could not reach $target; stuck on ${state.step}", target, state.step)
    }

    @Test
    fun `starts on the first question with nothing answered`() {
        assertEquals(OnboardingStep.GOAL, state.step)
        assertEquals(1, state.stepNumber)
        assertNull(state.goal)
        assertNull(state.experience)
    }

    @Test
    fun `cannot advance past the goal question until it is answered`() {
        assertFalse(state.canAdvance)

        viewModel.onNext()
        assertEquals("Next must do nothing while the question is unanswered", OnboardingStep.GOAL, state.step)

        viewModel.onGoalSelected(TrainingGoal.HYPERTROPHY)
        assertTrue(state.canAdvance)

        viewModel.onNext()
        assertEquals(OnboardingStep.EXPERIENCE, state.step)
    }

    @Test
    fun `cannot advance past the experience question until it is answered`() {
        viewModel.onGoalSelected(TrainingGoal.STRENGTH)
        viewModel.onNext()

        assertEquals(OnboardingStep.EXPERIENCE, state.step)
        assertFalse(state.canAdvance)

        viewModel.onNext()
        assertEquals(OnboardingStep.EXPERIENCE, state.step)
    }

    @Test
    fun `the questions with defaults never block`() {
        answerRequiredQuestions()
        OnboardingStep.ordered
            .filter { it != OnboardingStep.GOAL && it != OnboardingStep.EXPERIENCE }
            .forEach { step ->
                while (state.step != step) viewModel.onNext()
                assertTrue("$step should not block advancing", state.canAdvance)
            }
    }

    @Test
    fun `back walks the steps and stops at the first`() {
        advanceTo(OnboardingStep.EQUIPMENT)

        viewModel.onBack()
        assertEquals(OnboardingStep.EXPERIENCE, state.step)

        viewModel.onBack()
        assertEquals(OnboardingStep.GOAL, state.step)

        viewModel.onBack()
        assertEquals("Back on the first step must not walk off the front", OnboardingStep.GOAL, state.step)
    }

    @Test
    fun `next stops at the last step rather than walking off the end`() {
        advanceTo(OnboardingStep.REVIEW)
        assertTrue(state.isLastStep)

        viewModel.onNext()
        assertEquals(OnboardingStep.REVIEW, state.step)
    }

    /**
     * Optional means Next works without an answer, not that a second button
     * exists to say so.
     */
    @Test
    fun `an optional question can be passed without answering it`() {
        advanceTo(OnboardingStep.MUSCLES)

        viewModel.onNext()

        assertEquals(OnboardingStep.AVOID, state.step)
        assertTrue("Moving on must not invent an answer", state.preferredMuscles.isEmpty())
    }

    /**
     * Review is last so that the last thing before committing is a look at what
     * is being committed. If a question is ever added after it, the flow ends on
     * a question again and the review stops being a review.
     */
    /**
     * Declining notifications must not block the flow: what it costs is the
     * background timer, not the app.
     */
    @Test
    fun `the notification step never blocks`() {
        advanceTo(OnboardingStep.NOTIFICATIONS)

        assertTrue(state.canAdvance)

        viewModel.onNext()
        assertEquals(OnboardingStep.REVIEW, state.step)
    }

    @Test
    fun `review is the final step and is where finish lives`() {
        assertEquals(OnboardingStep.REVIEW, OnboardingStep.ordered.last())

        advanceTo(OnboardingStep.REVIEW)
        assertTrue(state.isLastStep)
    }

    @Test
    fun `jumping from the review goes straight to that question and back again`() {
        advanceTo(OnboardingStep.REVIEW)

        viewModel.onJumpTo(OnboardingStep.DAYS)
        assertEquals(OnboardingStep.DAYS, state.step)
        assertEquals("Jumping must not discard an answer", TrainingGoal.STRENGTH, state.goal)
    }

    @Test
    fun `equipment toggles on and off`() {
        viewModel.onEquipmentToggled(Equipment.DUMBBELL)
        assertTrue(Equipment.DUMBBELL in state.equipment)

        viewModel.onEquipmentToggled(Equipment.DUMBBELL)
        assertTrue(Equipment.DUMBBELL !in state.equipment)
    }

    /**
     * Everyone has their own body weight, so there is no honest empty answer to
     * this question. Choosing it for them is also what lets the saved profile be
     * exactly what the screen showed.
     */
    @Test
    fun `body weight is chosen before the question is asked`() {
        assertEquals(setOf(Equipment.BODY_WEIGHT), state.equipment)
    }

    @Test
    fun `the equipment question cannot be left empty`() {
        advanceTo(OnboardingStep.EQUIPMENT)
        assertTrue(state.canAdvance)

        viewModel.onEquipmentToggled(Equipment.BODY_WEIGHT)

        assertTrue("Deselecting the last one must leave the set empty", state.equipment.isEmpty())
        assertFalse("Next must refuse an empty answer", state.canAdvance)

        viewModel.onNext()
        assertEquals(OnboardingStep.EQUIPMENT, state.step)

        viewModel.onEquipmentToggled(Equipment.KETTLEBELL)
        assertTrue(state.canAdvance)
    }

    @Test
    fun `every question except goal and experience and equipment can be passed`() {
        answerRequiredQuestions()
        OnboardingStep.ordered
            .filterNot {
                it == OnboardingStep.GOAL ||
                    it == OnboardingStep.EXPERIENCE ||
                    it == OnboardingStep.EQUIPMENT
            }
            .forEach { step ->
                while (state.step != step) viewModel.onNext()
                assertTrue("$step should not require an answer", state.canAdvance)
            }
    }

    @Test
    fun `sliders clamp to their declared range`() {
        viewModel.onDaysChanged(99)
        assertEquals(OnboardingUiState.DAYS_RANGE.last, state.trainingDaysPerWeek)

        viewModel.onDaysChanged(0)
        assertEquals(OnboardingUiState.DAYS_RANGE.first, state.trainingDaysPerWeek)

        viewModel.onSessionLengthChanged(9_000)
        assertEquals(OnboardingUiState.SESSION_MINUTES_RANGE.last, state.sessionLengthMinutes)
    }

    /**
     * The synonym rule, which is the same one the catalog filters use: selecting
     * a muscle selects every upstream name for it, or the profile is written
     * half-applied and the rules engine honours half of it.
     */
    @Test
    fun `selecting a muscle selects its whole synonym group`() {
        val withSynonyms = Muscle.entries.first { muscle ->
            Muscle.entries.count { it.canonical == muscle.canonical } > 1
        }
        val group = Muscle.entries.filter { it.canonical == withSynonyms.canonical }.toSet()

        viewModel.onPreferredMuscleToggled(withSynonyms)
        assertEquals(group, state.preferredMuscles)

        viewModel.onPreferredMuscleToggled(withSynonyms)
        assertTrue(state.preferredMuscles.isEmpty())
    }

    @Test
    fun `avoiding a muscle removes it from the preferred ones`() {
        val muscle = Muscle.entries.first()

        viewModel.onPreferredMuscleToggled(muscle)
        assertTrue(state.preferredMuscles.isNotEmpty())

        viewModel.onAvoidedMuscleToggled(muscle)

        assertTrue(
            "A muscle cannot be both favoured and forbidden",
            state.preferredMuscles.isEmpty(),
        )
        assertTrue(state.avoidedMuscles.isNotEmpty())
    }

    @Test
    fun `finishing writes a profile built from the answers`() = runTest(dispatcher) {
        viewModel.onGoalSelected(TrainingGoal.ENDURANCE)
        viewModel.onExperienceSelected(ExperienceLevel.ADVANCED)
        viewModel.onEquipmentToggled(Equipment.BARBELL)
        viewModel.onDaysChanged(5)
        viewModel.onSessionLengthChanged(60)

        viewModel.onFinish()
        testScheduler.advanceUntilIdle()

        val saved = profiles.saved.single()
        assertEquals(TrainingGoal.ENDURANCE, saved.goal)
        assertEquals(ExperienceLevel.ADVANCED, saved.experience)
        assertEquals(5, saved.trainingDaysPerWeek)
        assertEquals("60 minutes must be stored as milliseconds", 3_600_000L, saved.sessionLengthMs)
        assertEquals(setOf(Equipment.BODY_WEIGHT, Equipment.BARBELL), saved.availableEquipment)
        assertTrue(saved.id.isNotBlank())
    }

    @Test
    fun `avoided muscles become exclusions the rules engine can read back`() = runTest(dispatcher) {
        val muscle = Muscle.entries.first()
        answerRequiredQuestions()
        viewModel.onAvoidedMuscleToggled(muscle)

        viewModel.onFinish()
        testScheduler.advanceUntilIdle()

        val saved = profiles.saved.single()
        assertTrue(
            "The exclusion must survive the round trip through UserProfile",
            muscle.canonical in saved.excludedMuscles.map { it.canonical },
        )
    }

    /**
     * Found by an audit, not by the tests: the exclusion was one-way.
     *
     * Avoiding a muscle dropped it from preferred, but preferring one did not
     * drop it from avoided — and the Back button makes that reachable in about
     * four taps. The profile then asked the rules engine to favour and forbid
     * the same muscle.
     */
    @Test
    fun `preferring a muscle removes it from the avoided ones`() {
        val muscle = Muscle.entries.first()

        viewModel.onAvoidedMuscleToggled(muscle)
        assertTrue(state.avoidedMuscles.isNotEmpty())

        viewModel.onPreferredMuscleToggled(muscle)

        assertTrue(
            "Preferring must clear the avoidance, the same way avoiding clears the preference",
            state.avoidedMuscles.isEmpty(),
        )
        assertTrue(state.preferredMuscles.isNotEmpty())
    }

    @Test
    fun `the two muscle sets can never overlap, whichever order they are answered in`() {
        val region = BodyRegion.entries.first { it.muscles.isNotEmpty() }

        viewModel.onAvoidedRegionToggled(region)
        viewModel.onPreferredRegionToggled(region)
        assertTrue(state.preferredMuscles.intersect(state.avoidedMuscles).isEmpty())

        viewModel.onAvoidedRegionToggled(region)
        assertTrue(state.preferredMuscles.intersect(state.avoidedMuscles).isEmpty())
    }

    /**
     * The equipment step says "choosing nothing means body weight only", and an
     * empty set does not mean that: [UserProfile] documents empty as "unknown"
     * and the rules engine then permits every piece of equipment there is. So
     * the screen promised a bodyweight plan and the engine would have
     * programmed barbells.
     */
    /**
     * The profile is saved as shown, with no step in between that could disagree
     * with it. That equality is the point of pre-selecting rather than
     * substituting: an empty set still means "unrestricted" to the rules engine,
     * and onboarding no longer produces one.
     */
    @Test
    fun `the saved equipment is exactly what the screen showed`() = runTest(dispatcher) {
        answerRequiredQuestions()
        viewModel.onEquipmentToggled(Equipment.KETTLEBELL)
        val shown = state.equipment

        viewModel.onFinish()
        testScheduler.advanceUntilIdle()

        assertEquals(shown, profiles.saved.single().availableEquipment)
        assertTrue(profiles.saved.single().availableEquipment.isNotEmpty())
    }

    @Test
    fun `choosing equipment saves exactly what was chosen`() = runTest(dispatcher) {
        answerRequiredQuestions()
        viewModel.onEquipmentToggled(Equipment.DUMBBELL)

        viewModel.onFinish()
        testScheduler.advanceUntilIdle()

        assertEquals(
            setOf(Equipment.BODY_WEIGHT, Equipment.DUMBBELL),
            profiles.saved.single().availableEquipment,
        )
    }

    /**
     * Found on a device: resetting the app dropped the user at the *end* of
     * onboarding with every button greyed out.
     *
     * This ViewModel outlives the profile — it is scoped to the activity, and
     * the app decides between onboarding and the app by whether a profile
     * exists. So after a reset the questionnaire was still sitting on the review
     * step of the run that had just been erased, with `saving` left true from a
     * write that had long since finished, which disabled the only button left.
     */
    @Test
    fun `deleting the profile starts the questionnaire again`() = runTest(dispatcher) {
        answerRequiredQuestions()
        while (!state.isLastStep) viewModel.onNext()
        viewModel.onFinish()
        testScheduler.advanceUntilIdle()
        assertEquals(1, profiles.saved.size)
        assertFalse("Saving must not stay true after the write", state.saving)

        profiles.deleteAll()
        testScheduler.advanceUntilIdle()

        assertEquals("A reset asks the questions again", OnboardingStep.GOAL, state.step)
        assertNull(state.goal)
        assertNull(state.experience)
        assertFalse(state.saving)
        assertEquals(
            "Even the pre-selected answer starts over",
            setOf(Equipment.BODY_WEIGHT),
            state.equipment,
        )
    }

    @Test
    fun `finishing without the required answers writes nothing`() = runTest(dispatcher) {
        viewModel.onFinish()
        testScheduler.advanceUntilIdle()

        assertTrue(
            "A profile must never be built from an incomplete questionnaire",
            profiles.saved.isEmpty(),
        )
    }

    /**
     * Double-tapping Finish on a slow write would otherwise create two profiles,
     * and the app would then start with whichever one the query returned first.
     */
    @Test
    fun `finishing twice writes one profile`() = runTest(dispatcher) {
        answerRequiredQuestions()

        viewModel.onFinish()
        viewModel.onFinish()
        testScheduler.advanceUntilIdle()

        assertEquals(1, profiles.saved.size)
    }
}

private class RecordingProfileRepository : ProfileRepository {
    val saved = mutableListOf<UserProfile>()
    private val profile = MutableStateFlow<UserProfile?>(null)

    override fun observeProfile(): Flow<UserProfile?> = profile

    override suspend fun getProfile(): UserProfile? = profile.value

    override suspend fun save(profile: UserProfile) {
        saved += profile
        this.profile.value = profile
    }

    override suspend fun deleteAll() {
        saved.clear()
        profile.value = null
    }
}
