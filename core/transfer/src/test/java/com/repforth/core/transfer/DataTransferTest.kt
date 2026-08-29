package com.repforth.core.transfer

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.datastore.UserPreferencesDataSource
import com.repforth.core.model.Equipment
import com.repforth.core.model.ThemeMode
import com.repforth.core.model.UserPreferences
import com.repforth.core.model.ExclusionKind
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.ExperienceLevel
import com.repforth.core.model.MovementExclusion
import com.repforth.core.model.Muscle
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingGoal
import com.repforth.core.model.UserProfile
import com.repforth.core.model.WorkoutTemplate
import com.repforth.core.workout.SessionExercise
import com.repforth.core.workout.SessionPhase
import com.repforth.core.workout.SessionSnapshot
import com.repforth.core.workout.SetOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Export and import, which between them are the only way a user's data leaves
 * or enters the phone (§7).
 *
 * The tests that matter here are the ones about not losing anything: a round
 * trip has to return exactly what went in, and a file that cannot be trusted has
 * to be refused *before* anything is written, because the copy being overwritten
 * is the only copy there is.
 */
class DataTransferTest {

    private lateinit var profiles: FakeProfiles
    private lateinit var templates: FakeTemplates
    private lateinit var sessions: FakeSessions
    private lateinit var preferences: UserPreferencesDataSource
    private lateinit var transfer: DataTransfer

    @Before
    fun setUp() {
        profiles = FakeProfiles()
        templates = FakeTemplates()
        sessions = FakeSessions()
        preferences = fakePreferences()
        transfer = DefaultDataTransfer(
            profiles, templates, sessions, preferences, FakeTimeSource(),
        )
    }

    private suspend fun seed() {
        profiles.save(sampleProfile())
        templates.save(sampleTemplate("plan-1", "Push day"))
        templates.save(sampleTemplate("plan-2", "Pull day"))
        sessions.persist(sampleSession())
    }

    /**
     * Compares the restored objects with the originals, not one export with
     * another.
     *
     * The export-to-export version of this test was written first and is
     * worthless: if the mapping drops a field, both sides drop it identically
     * and the strings still match. Verified by deleting exclusions from the
     * export mapping — the string comparison stayed green, and this does not.
     */
    @Test
    fun `a round trip returns exactly what went in`() = runTest {
        seed()
        val exported = transfer.export()

        val restoredProfiles = FakeProfiles()
        val restoredTemplates = FakeTemplates()
        val restoredSessions = FakeSessions()
        val emptied = DefaultDataTransfer(
            restoredProfiles, restoredTemplates, restoredSessions,
            fakePreferences(), FakeTimeSource(),
        )

        val outcome = emptied.read(exported)
        assertTrue("A file this app wrote must be readable", outcome is ImportOutcome.Ready)
        emptied.import((outcome as ImportOutcome.Ready).document)

        assertEquals(
            "The profile must survive the file, field for field",
            sampleProfile(),
            restoredProfiles.stored,
        )
        assertEquals(
            templates.stored.sortedBy { it.id },
            restoredTemplates.stored.sortedBy { it.id },
        )
        assertEquals(
            sessions.stored.sortedBy { it.sessionId },
            restoredSessions.stored.sortedBy { it.sessionId },
        )
    }

    @Test
    fun `the exported document identifies itself`() = runTest {
        seed()

        val exported = transfer.export()

        assertTrue(exported.contains("\"format\": \"${ExportDocument.FORMAT}\""))
        assertTrue(exported.contains("\"version\": ${ExportDocument.VERSION}"))
    }

    @Test
    fun `an empty app exports a readable, empty document`() = runTest {
        val exported = transfer.export()

        val outcome = transfer.read(exported)
        assertTrue(outcome is ImportOutcome.Ready)
        assertTrue((outcome as ImportOutcome.Ready).preview.isEmpty)
    }

    @Test
    fun `something that is not json is refused`() = runTest {
        val outcome = transfer.read("this is not a file")

        assertTrue(outcome is ImportOutcome.Failed)
        assertTrue((outcome as ImportOutcome.Failed).failure is ImportFailure.Unreadable)
    }

    @Test
    fun `json from some other app is refused`() = runTest {
        val outcome = transfer.read("""{"format":"someoneelse","version":1,"exportedAt":0}""")

        val failure = (outcome as ImportOutcome.Failed).failure
        assertTrue(failure is ImportFailure.WrongFormat)
        assertEquals("someoneelse", (failure as ImportFailure.WrongFormat).found)
    }

    /**
     * A newer file may use fields whose meaning had not been decided when this
     * code was written. Reading it anyway is how an import quietly loses data.
     */
    @Test
    fun `a file from a newer version is refused rather than guessed at`() = runTest {
        val outcome = transfer.read(
            """{"format":"${ExportDocument.FORMAT}","version":99,"exportedAt":0}""",
        )

        val failure = (outcome as ImportOutcome.Failed).failure
        assertTrue(failure is ImportFailure.TooNew)
        assertEquals(99, (failure as ImportFailure.TooNew).found)
    }

    @Test
    fun `a file whose contents break a domain rule is refused before anything is written`() =
        runTest {
            // A plan with no name. WorkoutTemplate requires one.
            val json = """
                {"format":"${ExportDocument.FORMAT}","version":1,"exportedAt":0,
                 "templates":[{"id":"x","name":"","source":"MANUAL","exercises":[]}]}
            """.trimIndent()

            val outcome = transfer.read(json)

            assertTrue((outcome as ImportOutcome.Failed).failure is ImportFailure.Invalid)
            assertTrue("Nothing may be written by a read", templates.stored.isEmpty())
        }

    @Test
    fun `an unknown enum name is refused, naming the field`() = runTest {
        val json = """
            {"format":"${ExportDocument.FORMAT}","version":1,"exportedAt":0,
             "profile":{"id":"p","goal":"BECOMING_A_BIRD","experience":"BEGINNER",
                        "trainingDaysPerWeek":3,"sessionLengthMs":1000}}
        """.trimIndent()

        val outcome = transfer.read(json)

        val failure = (outcome as ImportOutcome.Failed).failure as ImportFailure.Invalid
        assertTrue(
            "The message should say which field was wrong, not just the value",
            failure.detail.contains("goal"),
        )
    }

    @Test
    fun `the preview counts what would be added and what would be replaced`() = runTest {
        templates.save(sampleTemplate("plan-1", "Existing"))
        profiles.save(sampleProfile())

        val other = DefaultDataTransfer(
            FakeProfiles(), FakeTemplates(), FakeSessions(), fakePreferences(), FakeTimeSource(),
        )
        other.import(
            ExportDocument(
                exportedAt = 0,
                profile = sampleProfile().toDto(),
                templates = listOf(
                    sampleTemplate("plan-1", "Incoming").toDto(),
                    sampleTemplate("plan-9", "Brand new").toDto(),
                ),
            ),
        )
        val file = other.export()

        val preview = (transfer.read(file) as ImportOutcome.Ready).preview

        assertEquals(1, preview.replacedTemplates)
        assertEquals(1, preview.newTemplates)
        assertTrue(preview.replacesExistingProfile)
        assertFalse(preview.isEmpty)
    }

    @Test
    fun `reading writes nothing at all`() = runTest {
        seed()
        val exported = transfer.export()
        val before = templates.stored.size to sessions.stored.size

        transfer.read(exported)

        assertEquals(before, templates.stored.size to sessions.stored.size)
    }

    /**
     * §7 is explicit: deleting workout data must not take the catalog with it.
     * The catalog is not the user's data, and re-importing 1,324 exercises
     * because someone cleared their history would be a bug, not a cleanup.
     */
    @Test
    fun `deleting workout data clears user data and leaves preferences alone`() = runTest {
        seed()
        preferences.setThemeMode(ThemeMode.DARK)

        transfer.deleteWorkoutData()

        assertEquals(null, profiles.stored)
        assertTrue(templates.stored.isEmpty())
        assertTrue(sessions.stored.isEmpty())
        assertEquals(
            "Preferences are not workout data",
            ThemeMode.DARK,
            preferences.preferences.first().themeMode,
        )
    }

    @Test
    fun `resetting the app also forgets preferences`() = runTest {
        seed()
        preferences.setThemeMode(ThemeMode.DARK)

        transfer.resetApp()

        assertEquals(null, profiles.stored)
        assertTrue(templates.stored.isEmpty())
        assertTrue(sessions.stored.isEmpty())
        assertEquals(
            "Reset returns preferences to their defaults",
            UserPreferences.Default.themeMode,
            preferences.preferences.first().themeMode,
        )
    }

    @Test
    fun `a session round trip keeps every set that was performed`() = runTest {
        sessions.persist(sampleSession())

        val exported = transfer.export()
        val restored = (transfer.read(exported) as ImportOutcome.Ready)
            .document.sessions.single().toDomain()

        val original = sampleSession()
        assertEquals(original.exercises.size, restored.exercises.size)
        assertEquals(
            original.exercises.first().sets,
            restored.exercises.first().sets,
        )
        assertEquals(SessionPhase.COMPLETED, restored.phase)
    }

    @Test
    fun `template exercises out of order in the file come back in order`() = runTest {
        val scrambled = TemplateDto(
            id = "plan-x",
            name = "Scrambled",
            source = PlanSource.MANUAL.name,
            exercises = listOf(
                PlannedExerciseDto("b", "ex-1", position = 1, sets = 3, reps = 10, restMs = 60_000),
                PlannedExerciseDto("a", "ex-0", position = 0, sets = 3, reps = 10, restMs = 60_000),
            ),
        )

        val plan = scrambled.toDomain()

        assertEquals(listOf(0, 1), plan.exercises.map { it.position })
        assertEquals(listOf("a", "b"), plan.exercises.map { it.id })
    }
}

private fun sampleProfile() = UserProfile(
    id = "profile-1",
    goal = TrainingGoal.HYPERTROPHY,
    experience = ExperienceLevel.INTERMEDIATE,
    trainingDaysPerWeek = 4,
    sessionLengthMs = 45 * 60_000L,
    availableEquipment = setOf(Equipment.BODY_WEIGHT, Equipment.DUMBBELL),
    preferredMuscles = setOf(Muscle.entries.first()),
    exclusions = setOf(MovementExclusion(ExclusionKind.MUSCLE, "abs")),
)

private fun sampleTemplate(id: String, name: String) = WorkoutTemplate(
    id = id,
    name = name,
    notes = "a note",
    source = PlanSource.MANUAL,
    exercises = listOf(
        PlannedExercise("row-1", ExerciseId("ex-0"), 0, ExerciseTarget.Reps(3, 10, 60.0), 90_000),
        PlannedExercise("row-2", ExerciseId("ex-1"), 1, ExerciseTarget.Duration(2, 30_000), 60_000),
    ),
)

private fun sampleSession() = SessionSnapshot(
    sessionId = "session-1",
    templateId = "plan-1",
    phase = SessionPhase.COMPLETED,
    exercises = listOf(
        SessionExercise(
            id = "se-1",
            exerciseId = ExerciseId("ex-0"),
            position = 0,
            target = ExerciseTarget.Reps(3, 10, 60.0),
            restMs = 90_000,
            sets = listOf(
                SetOutcome(0, skipped = false, reps = 10, weightKg = 60.0, recordedAt = 1_000),
                SetOutcome(1, skipped = true, recordedAt = 2_000),
            ),
        ),
    ),
    startedAt = 500,
    endedAt = 3_000,
)
