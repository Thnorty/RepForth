package com.repforth.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The project's first migration, exercised against a real SQLite (§7, §18).
 *
 * This is the only test in `core:database` that needs a device, and it needs one
 * for the reason that makes it worth having: a migration is a statement about
 * what SQLite does to a file, and the JVM tests here read the *exported schema*,
 * which is a description of what Room expects rather than of what the migration
 * produces. Those two agreeing is exactly the thing that can be wrong.
 *
 * §7 forbids destructive migration, so what this proves is not only that the new
 * shape arrives but that the rows already in the file are still there
 * afterwards. `runMigrationsAndValidate` additionally makes Room compare the
 * migrated database against `2.json` column by column, index by index, foreign
 * key by foreign key — so a migration that produces a *nearly* correct table
 * fails here instead of at launch on someone's phone.
 *
 * Run with:
 *   ./gradlew :core:database:connectedAndroidTest
 *
 * Note the warning in AGENTS.md before running it on a phone that holds data
 * worth keeping: `connectedAndroidTest` uninstalls the app under test when it
 * finishes, and uninstalling wipes its files.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RepForthDatabase::class.java,
    )

    /**
     * The structural half: Room accepts what the migration built.
     *
     * If the `ALTER TABLE` statements produce a `workout_template` whose foreign
     * key, index, or column types differ in any way from the entity Room
     * generates, `runMigrationsAndValidate` throws here rather than every
     * existing install crashing on first launch after the update.
     */
    @Test
    fun migrating_from_1_to_2_produces_the_schema_room_expects() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            RepForthDatabase.MIGRATION_1_2,
        ).close()
    }

    /**
     * The half that matters to the user: nothing already saved is lost.
     *
     * A migration that drops the table and recreates it would pass the check
     * above and fail this one, which is the whole distinction §7 draws when it
     * says never to use destructive migration.
     */
    @Test
    fun migrating_from_1_to_2_keeps_the_plans_already_saved() {
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(
                """
                INSERT INTO workout_template
                    (id, name, notes, source, created_at, updated_at)
                VALUES ('plan-1', 'Leg day', 'heavy', 'MANUAL', 100, 200)
                """.trimIndent(),
            )
            v1.execSQL(
                """
                INSERT INTO template_exercise
                    (id, template_id, exercise_id, position, target_sets, target_reps,
                     target_duration_ms, target_weight_kg, rest_ms, created_at, updated_at)
                VALUES ('row-1', 'plan-1', 'ex-1', 0, 3, 10, NULL, 60.0, 90000, 100, 200)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            RepForthDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT name, week_id, week_position, day_of_week FROM workout_template")
            .use { cursor ->
                assertTrue("The saved plan must survive the migration", cursor.moveToFirst())
                assertEquals(1, cursor.count)
                assertEquals("Leg day", cursor.getString(0))
                // Every plan that existed before weeks existed is a standalone
                // workout, and that is what a null week_id means. If these came
                // back non-null the `week_id IS NULL` filter in TemplateDao
                // would hide every plan the user already had.
                assertTrue("An existing plan must stay standalone", cursor.isNull(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }

        migrated.query("SELECT target_sets, target_reps, rest_ms FROM template_exercise")
            .use { cursor ->
                assertTrue("Its exercises must survive too", cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals(10, cursor.getInt(1))
                assertEquals(90_000L, cursor.getLong(2))
            }

        migrated.close()
    }

    /**
     * The new table exists and is empty.
     *
     * Stated separately because "the schema validates" and "the table is usable"
     * are different claims, and a week saved into a table that validated but
     * rejects inserts is a failure the user meets rather than the build.
     */
    @Test
    fun migrating_from_1_to_2_adds_an_empty_training_week_table() {
        helper.createDatabase(TEST_DB, 1).close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            RepForthDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT COUNT(*) FROM training_week").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A migrated database starts with no weeks", 0, cursor.getInt(0))
        }

        migrated.execSQL(
            """
            INSERT INTO training_week (id, name, notes, source, active, created_at, updated_at)
            VALUES ('week-1', 'Split', NULL, 'AI', 1, 100, 200)
            """.trimIndent(),
        )
        migrated.query("SELECT name, active FROM training_week").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Split", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }

        migrated.close()
    }

    /**
     * Deleting a week takes its days with it, in the database rather than in
     * Kotlin.
     *
     * This is the maintainer's decision — delete, do not detach — and enforcing
     * it with a foreign key means it holds for any caller, including a future
     * one that forgets. `PRAGMA foreign_keys` has to be turned on explicitly: it
     * is off by default on a raw connection, and a cascade that is only
     * *declared* is not a cascade.
     */
    @Test
    fun deleting_a_week_cascades_to_its_days_after_migrating() {
        helper.createDatabase(TEST_DB, 1).close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            RepForthDatabase.MIGRATION_1_2,
        )

        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL(
            """
            INSERT INTO training_week (id, name, notes, source, active, created_at, updated_at)
            VALUES ('week-1', 'Split', NULL, 'AI', 1, 100, 200)
            """.trimIndent(),
        )
        migrated.execSQL(
            """
            INSERT INTO workout_template
                (id, name, notes, source, week_id, week_position, day_of_week, created_at, updated_at)
            VALUES ('day-1', 'Push', NULL, 'AI', 'week-1', 0, NULL, 100, 200)
            """.trimIndent(),
        )

        migrated.execSQL("DELETE FROM training_week WHERE id = 'week-1'")

        migrated.query("SELECT COUNT(*) FROM workout_template WHERE id = 'day-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "Deleting a week must delete its days; leaving them behind is " +
                    "the 'detach' behaviour that was explicitly not chosen.",
                0,
                cursor.getInt(0),
            )
        }

        migrated.close()
    }

    /**
     * A standalone workout is untouched when an unrelated week is deleted.
     *
     * The cascade is scoped by `week_id`; if it were ever widened, this is what
     * would notice.
     */
    @Test
    fun deleting_a_week_leaves_standalone_workouts_alone() {
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(
                """
                INSERT INTO workout_template
                    (id, name, notes, source, created_at, updated_at)
                VALUES ('loose-1', 'My own plan', NULL, 'MANUAL', 100, 200)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            RepForthDatabase.MIGRATION_1_2,
        )

        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL(
            """
            INSERT INTO training_week (id, name, notes, source, active, created_at, updated_at)
            VALUES ('week-1', 'Split', NULL, 'AI', 1, 100, 200)
            """.trimIndent(),
        )
        migrated.execSQL("DELETE FROM training_week WHERE id = 'week-1'")

        migrated.query("SELECT week_id FROM workout_template WHERE id = 'loose-1'").use { cursor ->
            assertTrue("A standalone plan must not be collateral damage", cursor.moveToFirst())
            assertFalse(cursor.isAfterLast)
            assertNull(cursor.getString(0))
        }

        migrated.close()
    }

    /**
     * The second migration's structural half.
     *
     * Started from a real version 2 rather than from 1, because that is what is
     * on the phones this update reaches: `2.json` is the committed record of
     * that shape, and `createDatabase` builds it from that file.
     */
    @Test
    fun migrating_from_2_to_3_produces_the_schema_room_expects() {
        helper.createDatabase(TEST_DB, 2).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            RepForthDatabase.MIGRATION_2_3,
        ).close()
    }

    /** And the whole chain, which is what a v1 install actually runs. */
    @Test
    fun migrating_from_1_to_3_produces_the_schema_room_expects() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            RepForthDatabase.MIGRATION_1_2,
            RepForthDatabase.MIGRATION_2_3,
        ).close()
    }

    /**
     * The half that matters to the user: the workout they were in the middle of
     * is still there, and still says where they had got to.
     *
     * Version 3 stores the cursor because deriving it from the set records was
     * wrong in two states. A migration that only added the columns would leave
     * everybody mid-workout at the first set of the first exercise — correct by
     * the schema and wrong on the device — so the backfill reproduces the old
     * derivation, and this is what says it ran.
     */
    @Test
    fun migrating_from_2_to_3_backfills_where_the_user_had_got_to() {
        helper.createDatabase(TEST_DB, 2).use { v2 ->
            v2.insertSessionInProgress()
            // Exercise 0 is finished: three of three. Exercise 1 has one set
            // done, so that is where the user is standing.
            v2.insertExercise("ex-0", position = 0, targetSets = 3)
            v2.insertExercise("ex-1", position = 1, targetSets = 3)
            repeat(3) { v2.insertSet("ex-0", it) }
            v2.insertSet("ex-1", 0)
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            RepForthDatabase.MIGRATION_2_3,
        )

        migrated.query(
            "SELECT state, current_exercise_index, current_set_index, rest_remaining_ms, revision " +
                "FROM workout_session",
        ).use { cursor ->
            assertTrue("The workout in progress must survive the migration", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("ACTIVE", cursor.getString(0))
            assertEquals("The exercise still owed sets", 1, cursor.getInt(1))
            assertEquals("The sets already recorded against it", 1, cursor.getInt(2))
            assertTrue("Nothing was paused, so there is no remainder", cursor.isNull(3))
            assertEquals(7L, cursor.getLong(4))
        }

        migrated.query("SELECT COUNT(*) FROM set_record").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Every set the user performed must survive", 4, cursor.getInt(0))
        }

        migrated.close()
    }

    /**
     * The fallback arm, which the old code also had: a session owing nothing
     * lands on its last exercise rather than on exercise zero.
     */
    @Test
    fun migrating_from_2_to_3_lands_a_complete_session_on_its_last_exercise() {
        helper.createDatabase(TEST_DB, 2).use { v2 ->
            v2.insertSessionInProgress(state = "COMPLETING")
            v2.insertExercise("ex-0", position = 0, targetSets = 2)
            v2.insertExercise("ex-1", position = 1, targetSets = 2)
            repeat(2) { v2.insertSet("ex-0", it) }
            repeat(2) { v2.insertSet("ex-1", it) }
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            RepForthDatabase.MIGRATION_2_3,
        )

        migrated.query(
            "SELECT current_exercise_index, current_set_index FROM workout_session",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1))
        }

        migrated.close()
    }

    /** A session with no exercises at all must not break the backfill's subqueries. */
    @Test
    fun migrating_from_2_to_3_handles_a_session_with_no_exercises() {
        helper.createDatabase(TEST_DB, 2).use { v2 ->
            v2.insertSessionInProgress(state = "PREPARING")
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            RepForthDatabase.MIGRATION_2_3,
        )

        migrated.query(
            "SELECT current_exercise_index, current_set_index FROM workout_session",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }

        migrated.close()
    }

    /**
     * v3 to v4: a timed set gets its own clock, and nothing already saved moves.
     *
     * Both columns are nullable with no backfill, which is correct rather than
     * lazy — null means "no timed set is counting", and that is true of every
     * row written before timed sets existed, because there were none.
     */
    @Test
    fun migrating_from_3_to_4_adds_the_set_clock_and_keeps_the_workout() {
        helper.createDatabase(TEST_DB, 3).use { v3 ->
            v3.execSQL(
                """
                INSERT INTO workout_session
                    (id, template_id, state, phase_before_pause, deadline_at,
                     rest_remaining_ms, current_exercise_index, current_set_index,
                     started_at, ended_at, revision, created_at, updated_at)
                VALUES ('session-1', 'plan-1', 'ACTIVE', NULL, NULL, NULL, 1, 2,
                        100, NULL, 7, 100, 200)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            RepForthDatabase.MIGRATION_3_4,
        )

        migrated.query(
            "SELECT current_exercise_index, current_set_index, set_deadline_at, " +
                "set_remaining_ms FROM workout_session",
        ).use { cursor ->
            assertTrue("The workout in progress must survive", cursor.moveToFirst())
            assertEquals("Where the user was, untouched", 1, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1))
            assertTrue("No timed set was counting, because there were none", cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }

        migrated.close()
    }

    /** And the whole chain, which is what a v1 install runs. */
    @Test
    fun migrating_from_1_to_4_produces_the_schema_room_expects() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            RepForthDatabase.MIGRATION_1_2,
            RepForthDatabase.MIGRATION_2_3,
            RepForthDatabase.MIGRATION_3_4,
        ).close()
    }

    private fun SupportSQLiteDatabase.insertSessionInProgress(state: String = "ACTIVE") = execSQL(
        """
        INSERT INTO workout_session
            (id, template_id, state, phase_before_pause, deadline_at,
             started_at, ended_at, revision, created_at, updated_at)
        VALUES ('session-1', 'plan-1', '$state', NULL, NULL, 100, NULL, 7, 100, 200)
        """.trimIndent(),
    )

    private fun SupportSQLiteDatabase.insertExercise(id: String, position: Int, targetSets: Int) =
        execSQL(
            """
            INSERT INTO session_exercise
                (id, session_id, exercise_id, position, target_sets, target_reps,
                 target_duration_ms, target_weight_kg, rest_ms, created_at, updated_at)
            VALUES ('$id', 'session-1', 'catalog-$position', $position, $targetSets, 10,
                    NULL, 60.0, 90000, 100, 200)
            """.trimIndent(),
        )

    private fun SupportSQLiteDatabase.insertSet(exerciseId: String, position: Int) = execSQL(
        """
        INSERT INTO set_record
            (id, session_exercise_id, position, outcome, reps, weight_kg,
             duration_ms, rpe, recorded_at, created_at, updated_at)
        VALUES ('$exerciseId:$position', '$exerciseId', $position, 'COMPLETED', 10, 60.0,
                NULL, NULL, 300, 300, 300)
        """.trimIndent(),
    )

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
