package com.repforth.core.database

import androidx.room.testing.MigrationTestHelper
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
