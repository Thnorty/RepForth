package com.repforth.core.database

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The data tests §17 asks for, run against the actual packaged catalog.
 *
 * Opened read-only over JDBC rather than through Room, so this needs no device:
 * the file Room will copy on first launch is the file being inspected here.
 *
 * These matter because the database is a build artifact produced by a script
 * outside Gradle. Nothing else would notice if someone committed a stale one, or
 * one built from an older pin, or one missing half its Turkish.
 */
class PackagedCatalogTest {

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { read(it) }
        }

    private fun count(sql: String): Int = query(sql) { it.next(); it.getInt(1) }

    @Test
    fun `the packaged database carries the identity hash this build expects`() {
        // Room compares these on first open. A mismatch means the asset was
        // built from different entities, and Room throws rather than reading a
        // table whose shape it does not know — so catching it here is the
        // difference between a failed build and a crash on a user's phone.
        val schema = Json.parseToJsonElement(
            File("schemas/${RepForthDatabase::class.qualifiedName}/1.json").readText(),
        ) as JsonObject
        val expected = schema.getValue("database").jsonObject
            .getValue("identityHash").jsonPrimitive.content

        val actual = query("SELECT identity_hash FROM room_master_table WHERE id = 42") {
            it.next(); it.getString(1)
        }
        assertEquals(
            "The asset and the entities disagree. Re-run tools/import-dataset.py.",
            expected,
            actual,
        )
    }

    @Test
    fun `the packaged database declares the schema version`() {
        // Without this Room reads version 0 and looks for a migration to 1.
        assertEquals(RepForthDatabase.VERSION, query("PRAGMA user_version") { it.next(); it.getInt(1) })
    }

    @Test
    fun `every exercise the dataset documents is present`() {
        assertEquals(EXPECTED_RECORDS, count("SELECT COUNT(*) FROM exercise"))
    }

    @Test
    fun `exercise ids are unique and never blank`() {
        assertEquals(count("SELECT COUNT(*) FROM exercise"), count("SELECT COUNT(DISTINCT id) FROM exercise"))
        assertEquals(0, count("SELECT COUNT(*) FROM exercise WHERE TRIM(id) = ''"))
    }

    @Test
    fun `every exercise has instructions in both languages`() {
        // §13: the two ship in lockstep. An exercise with only English is one a
        // Turkish user opens to find blank.
        val incomplete = count(
            """
            SELECT COUNT(*) FROM exercise e WHERE (
              SELECT COUNT(DISTINCT language) FROM exercise_instruction_step s
              WHERE s.exercise_id = e.id AND s.language IN ('en', 'tr')
            ) < 2
            """.trimIndent(),
        )
        assertEquals("exercises missing a translation", 0, incomplete)
    }

    @Test
    fun `only the two supported languages were packaged`() {
        // The dataset ships ten. Packaging the other eight would be several MB
        // of text nothing can display.
        val languages = query("SELECT DISTINCT language FROM exercise_instruction_step ORDER BY language") {
            buildList { while (it.next()) add(it.getString(1)) }
        }
        assertEquals(listOf("en", "tr"), languages)
    }

    @Test
    fun `instruction steps are contiguous from zero`() {
        // Position is the display order. A gap means a step was dropped during
        // normalisation without the positions being rebuilt.
        val broken = count(
            """
            SELECT COUNT(*) FROM (
              SELECT exercise_id, language, MIN(position) AS lo,
                     MAX(position) AS hi, COUNT(*) AS n
              FROM exercise_instruction_step GROUP BY exercise_id, language
            ) WHERE lo <> 0 OR hi <> n - 1
            """.trimIndent(),
        )
        assertEquals("step positions with gaps", 0, broken)
    }

    @Test
    fun `turkish text kept its characters`() {
        // Catches an encoding fault in the import, which would otherwise show up
        // as mojibake on a user's screen rather than as a failure here.
        val dotless = count("SELECT COUNT(*) FROM exercise_instruction_step WHERE language = 'tr' AND text LIKE '%ı%'")
        assertTrue("no dotless i anywhere in the Turkish text", dotless > 0)
        val gBreve = count("SELECT COUNT(*) FROM exercise_instruction_step WHERE language = 'tr' AND text LIKE '%ğ%'")
        assertTrue("no g-breve anywhere in the Turkish text", gBreve > 0)
    }

    @Test
    fun `the packaged database ships no user data`() {
        // The asset is built by a script from a dataset. If a user table were
        // ever non-empty in it, every fresh install would arrive carrying
        // somebody else's rows.
        listOf(
            "user_profile", "profile_equipment", "profile_preferred_muscle",
            "movement_exclusion", "workout_template", "template_exercise",
            "workout_session", "session_exercise", "set_record",
        ).forEach { table ->
            assertEquals("$table must be empty in the packaged asset", 0, count("SELECT COUNT(*) FROM $table"))
        }
    }

    @Test
    fun `no orphaned rows reference a missing exercise`() {
        assertEquals(0, count(
            "SELECT COUNT(*) FROM exercise_secondary_muscle m " +
                "WHERE NOT EXISTS (SELECT 1 FROM exercise e WHERE e.id = m.exercise_id)",
        ))
        assertEquals(0, count(
            "SELECT COUNT(*) FROM exercise_instruction_step s " +
                "WHERE NOT EXISTS (SELECT 1 FROM exercise e WHERE e.id = s.exercise_id)",
        ))
    }

    @Test
    fun `categorical columns hold upstream slugs, not enum names`() {
        // Storing `BODY_WEIGHT` would mean a renamed Kotlin constant silently
        // invalidating a packaged database that cannot be rebuilt on a device.
        val shouty = count("SELECT COUNT(*) FROM exercise WHERE equipment = UPPER(equipment) AND equipment LIKE '%\\_%' ESCAPE '\\'")
        assertEquals(0, shouty)
        assertTrue(count("SELECT COUNT(*) FROM exercise WHERE equipment = 'body weight'") > 0)
    }

    companion object {
        /** Matches `record_count` in dataset-version.toml. */
        private const val EXPECTED_RECORDS = 1324

        private lateinit var connection: Connection

        @BeforeClass
        @JvmStatic
        fun open() {
            val asset = File("src/main/assets/${RepForthDatabase.NAME}")
            assertTrue(
                "No packaged catalog at ${asset.absolutePath}. Run tools/import-dataset.py.",
                asset.exists(),
            )
            connection = DriverManager.getConnection("jdbc:sqlite:${asset.absolutePath}")
        }

        @AfterClass
        @JvmStatic
        fun close() {
            if (::connection.isInitialized) connection.close()
        }
    }
}
