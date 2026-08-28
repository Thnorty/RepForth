package com.repforth.core.exercisedata

import com.repforth.core.model.BodyPart
import com.repforth.core.model.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter's only real logic is synonym expansion, and it is the part a user
 * would notice being wrong: the dataset calls the same muscle `abs` in one field
 * and `abdominals` in another, so a filter that matched only the word the user
 * tapped would silently hide exercises.
 */
class CatalogFilterTest {

    @Test
    fun `an empty filter constrains nothing`() {
        assertTrue(CatalogFilter().isEmpty)
        assertTrue(CatalogFilter(query = "   ").isEmpty)
        assertFalse(CatalogFilter(query = "curl").isEmpty)
        assertFalse(CatalogFilter(bodyPart = BodyPart.CHEST).isEmpty)
        assertFalse(CatalogFilter(muscles = setOf(Muscle.LATS)).isEmpty)
    }

    @Test
    fun `selecting one half of a synonym pair matches both spellings`() {
        val slugs = CatalogFilter(muscles = setOf(Muscle.ABS)).muscleSlugs()
        assertTrue("abs" in slugs)
        assertTrue("selecting abs must also find records labelled abdominals", "abdominals" in slugs)
    }

    @Test
    fun `expansion works from either side of the pair`() {
        // Whether the user tapped `quads` or `quadriceps`, the same records match.
        assertEquals(
            CatalogFilter(muscles = setOf(Muscle.QUADS)).muscleSlugs().toSet(),
            CatalogFilter(muscles = setOf(Muscle.QUADRICEPS)).muscleSlugs().toSet(),
        )
    }

    @Test
    fun `expansion does not leak into muscles that are merely nearby`() {
        // `lower abs` is inside the abs but is a deliberate non-merge, so
        // selecting abs must not silently widen to it.
        val slugs = CatalogFilter(muscles = setOf(Muscle.ABS)).muscleSlugs()
        assertFalse("lower abs" in slugs)
        assertFalse("obliques" in slugs)
    }

    @Test
    fun `shoulders and deltoids both expand to the whole delt group`() {
        val fromDelts = CatalogFilter(muscles = setOf(Muscle.DELTS)).muscleSlugs().toSet()
        assertTrue(setOf("delts", "deltoids", "shoulders").all { it in fromDelts })
        assertFalse("rear deltoids is a deliberate non-merge", "rear deltoids" in fromDelts)
    }

    @Test
    fun `no muscles selected produces an empty list, not a match-nothing filter`() {
        // The repository turns this into `ignoreMuscles`, because SQL has no
        // empty IN. An empty list here that reached the query would match zero
        // rows and look like an empty catalog.
        assertEquals(emptyList<String>(), CatalogFilter().muscleSlugs())
    }

    @Test
    fun `every selected muscle survives expansion`() {
        Muscle.entries.forEach { muscle ->
            assertTrue(
                "${muscle.slug} disappeared from its own filter",
                muscle.slug in CatalogFilter(muscles = setOf(muscle)).muscleSlugs(),
            )
        }
    }
}
