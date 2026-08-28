package com.repforth.core.database

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural rules §7 states once and which would otherwise decay quietly, one
 * convenient exception at a time.
 *
 * Read from the exported schema rather than from the entity source, so what is
 * checked is what SQLite will actually be given.
 */
class UserDataSchemaTest {

    private val database: JsonObject by lazy {
        val file = File("schemas/${RepForthDatabase::class.qualifiedName}/1.json")
        assertTrue("No exported schema at ${file.absolutePath}", file.exists())
        (Json.parseToJsonElement(file.readText()) as JsonObject).getValue("database").jsonObject
    }

    private val entities: List<JsonObject>
        get() = database.getValue("entities").jsonArray.map { it.jsonObject }

    private fun table(entity: JsonObject) = entity.getValue("tableName").jsonPrimitive.content

    /**
     * The catalog's own tables. They cascade from `exercise` on purpose — that
     * is how a dataset update replaces the catalog cleanly — so the rule below
     * is about user data, which must survive exactly that operation.
     */
    private val catalogTables = setOf(
        "exercise", "exercise_secondary_muscle", "exercise_instruction_step",
    )

    private fun columns(entity: JsonObject) =
        entity.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }

    @Test
    fun `no user table has a foreign key to the catalog`() {
        // The load-bearing rule in this file. The catalog is replaced wholesale
        // when the dataset pin moves: a CASCADE from user data would delete a
        // person's training history along with a retired exercise, and a
        // RESTRICT would make the update impossible. Catalog ids are plain
        // columns, and a missing exercise is a display problem.
        val offenders = entities.filterNot { table(it) in catalogTables }.flatMap { entity ->
            entity["foreignKeys"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { it.getValue("table").jsonPrimitive.content == "exercise" }
                .map { table(entity) }
        }
        assertEquals(
            "A foreign key to `exercise` makes a dataset update destroy user history.",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `every mutable table has a uuid key and both timestamps`() {
        // Membership rows are exempt: they carry a composite key and are
        // created and destroyed with their parent, so an `updated_at` on them
        // would never be read. Naming them here makes each exemption a decision
        // rather than an omission.
        val membershipTables = catalogTables + setOf(
            "profile_equipment", "profile_preferred_muscle", "movement_exclusion",
        )

        entities.filterNot { table(it) in membershipTables }.forEach { entity ->
            val name = table(entity)
            val columns = columns(entity)
            assertTrue("$name has no `id` primary key", "id" in columns)
            assertTrue("$name is missing created_at", "created_at" in columns)
            assertTrue("$name is missing updated_at", "updated_at" in columns)

            val primaryKey = entity.getValue("primaryKey").jsonObject
                .getValue("columnNames").jsonArray.map { it.jsonPrimitive.content }
            assertEquals("$name should key on a single UUID", listOf("id"), primaryKey)
        }
    }

    @Test
    fun `stored units are declared in the column name`() {
        // §7 stores kilograms and milliseconds regardless of display preference.
        // A column called `weight` invites someone to write pounds into it; a
        // column called `weight_kg` does not.
        val ambiguous = entities.flatMap { entity ->
            columns(entity)
                .filter { column ->
                    (column.contains("weight") && !column.endsWith("_kg")) ||
                        (column.contains("duration") && !column.endsWith("_ms")) ||
                        (column.contains("length") && !column.endsWith("_ms"))
                }
                .map { "${table(entity)}.$it" }
        }
        assertEquals(
            "Weights are kilograms and durations are milliseconds; say so in the name.",
            emptyList<String>(),
            ambiguous,
        )
    }

    @Test
    fun `timestamps are stored as integers, not text`() {
        // §7 wants UTC instants. Stored as text they sort lexicographically,
        // compare by locale, and quietly acquire a timezone.
        val wrong = entities.flatMap { entity ->
            entity.getValue("fields").jsonArray.map { it.jsonObject }
                .filter { field ->
                    val name = field.getValue("columnName").jsonPrimitive.content
                    val type = field.getValue("affinity").jsonPrimitive.contentOrNull
                    (name.endsWith("_at") || name.endsWith("_ms")) && type != "INTEGER"
                }
                .map { "${table(entity)}.${it.getValue("columnName").jsonPrimitive.content}" }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `deleting a parent cannot orphan its children`() {
        // Every foreign key that does exist cascades. An orphaned set record is
        // invisible in every query and still counted by every total.
        val notCascading = entities.flatMap { entity ->
            entity["foreignKeys"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { it.getValue("onDelete").jsonPrimitive.content != "CASCADE" }
                .map { "${table(entity)} -> ${it.getValue("table").jsonPrimitive.content}" }
        }
        assertEquals(emptyList<String>(), notCascading)
    }
}
