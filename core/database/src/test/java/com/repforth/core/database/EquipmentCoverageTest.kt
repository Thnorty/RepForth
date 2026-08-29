package com.repforth.core.database

import com.repforth.core.model.Equipment
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Keeps [Equipment.COMMON] honest against the catalog it was measured from.
 *
 * Onboarding shows ten pieces of equipment up front and hides eighteen behind a
 * disclosure. That split was measured, not guessed — but a measurement written
 * into a list is a fact frozen at a moment, and the dataset is pinned to a
 * commit that will eventually move. Without this, a dataset update could make
 * the shown ten the wrong ten and nothing would say so; the screen would keep
 * looking reasonable while asking the wrong question.
 *
 * Deliberately not asserting exact percentages. The claim worth defending is
 * that these are the common ones and the hidden ones are marginal, not that
 * body weight is 24.5%. A test that pins the decimals fails on every dataset
 * refresh and teaches people to update the number without looking.
 */
class EquipmentCoverageTest {

    private fun countsBySlug(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT equipment, COUNT(*) AS n FROM exercise GROUP BY equipment",
            ).use { rows ->
                while (rows.next()) counts[rows.getString("equipment")] = rows.getInt("n")
            }
        }
        return counts
    }

    @Test
    fun `every equipment slug in the catalog is a known constant`() {
        val unknown = countsBySlug().keys.filter { Equipment.fromSlug(it) == null }

        assertTrue(
            "The catalog uses equipment the vocabulary does not know: $unknown",
            unknown.isEmpty(),
        )
    }

    @Test
    fun `the common list and the uncommon list together are every equipment`() {
        assertEquals(
            "COMMON and UNCOMMON must partition the enum, or the screen hides an option",
            Equipment.entries.toSet(),
            (Equipment.COMMON + Equipment.UNCOMMON).toSet(),
        )
        assertEquals(
            "COMMON and UNCOMMON must not overlap",
            Equipment.entries.size,
            Equipment.COMMON.size + Equipment.UNCOMMON.size,
        )
    }

    @Test
    fun `the common equipment covers most of the catalog`() {
        val counts = countsBySlug()
        val total = counts.values.sum()
        val covered = Equipment.COMMON.sumOf { counts[it.slug] ?: 0 }
        val share = covered.toDouble() / total

        assertTrue(
            "Equipment.COMMON covers only ${"%.1f".format(share * 100)}% of $total exercises. " +
                "The dataset has moved; re-measure and update the list.",
            share >= MINIMUM_COVERAGE,
        )
    }

    /**
     * The point of hiding the rest: each hidden item is genuinely marginal.
     *
     * If something behind the disclosure grows into a real share of the
     * catalog, it belongs in front of it.
     */
    @Test
    fun `no hidden equipment is more common than the least common shown one`() {
        val counts = countsBySlug()
        val leastShown = Equipment.COMMON.minOf { counts[it.slug] ?: 0 }
        val tooBig = Equipment.UNCOMMON.filter { (counts[it.slug] ?: 0) > leastShown }

        assertTrue(
            "Hidden behind 'show more' but more common than the least common shown " +
                "option ($leastShown exercises): ${tooBig.map(Equipment::slug)}",
            tooBig.isEmpty(),
        )
    }

    private companion object {
        /** Ten of twenty-eight covered 91% when the split was chosen. */
        const val MINIMUM_COVERAGE = 0.85

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
