package com.repforth.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

/**
 * Guards the exported schema.
 *
 * §7 requires explicit migrations from the first public release onward, and a
 * migration needs a committed record of the version it starts from. Room only
 * writes that record if `exportSchema` stays on and the schema directory stays
 * configured — both of which are easy to lose in a build refactor and produce no
 * error when they go missing. This fails instead.
 *
 * It also pins the table set, so adding or renaming a table without bumping the
 * database version is a test failure rather than a crash on a user's device.
 */
class SchemaExportTest {

    private val schema: String by lazy {
        val file = File("schemas/${RepForthDatabase::class.qualifiedName}/${RepForthDatabase.VERSION}.json")
        assertTrue(
            "No exported schema at ${file.absolutePath}. Either exportSchema was " +
                "turned off, or the Room convention plugin is no longer applied.",
            file.exists(),
        )
        file.readText()
    }

    @Test
    fun `exported schema matches the declared version`() {
        val version = Regex(""""version"\s*:\s*(\d+)""").find(schema)?.groupValues?.get(1)?.toInt()
        assertEquals(RepForthDatabase.VERSION, version)
    }

    @Test
    fun `exported schema contains exactly the expected tables`() {
        val tables = Regex(""""tableName"\s*:\s*"([^"]+)"""")
            .findAll(schema)
            .map { it.groupValues[1] }
            .toSortedSet()

        assertEquals(
            "The schema's table set changed. If this is intentional, bump " +
                "RepForthDatabase.VERSION, write the migration, and update this list.",
            sortedSetOf(
                // Catalog — read-only, rebuilt when the dataset pin moves.
                "exercise",
                "exercise_instruction_step",
                "exercise_secondary_muscle",
                // User data — the only copy that exists anywhere.
                "user_profile",
                "profile_equipment",
                "profile_preferred_muscle",
                "movement_exclusion",
                "workout_template",
                "template_exercise",
                "workout_session",
                "session_exercise",
                "set_record",
            ),
            tables,
        )
    }

    @Test
    fun `catalog tables have no destructive migration escape hatch`() {
        val source = File("src/main/java/com/repforth/core/database/di/DatabaseModule.kt").readText()
        assertTrue(
            "fallbackToDestructiveMigration would silently delete a user's only " +
                "copy of their history on a schema mismatch (§7).",
            "fallbackToDestructiveMigration" !in source,
        )
    }
}
