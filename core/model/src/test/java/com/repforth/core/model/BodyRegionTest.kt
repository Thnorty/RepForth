package com.repforth.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The body map is a lossy view onto the muscle vocabulary, so the two must stay
 * in step. A muscle with nowhere to go is invisible to anyone using the picker,
 * and a region with nothing in it is a shape that highlights and then returns no
 * exercises — both fail silently at runtime, so they fail here instead.
 */
class BodyRegionTest {

    @Test
    fun `every muscle is either on the map or explicitly not a place on the body`() {
        val unplaced = Muscle.entries.filter { it.region == null }
        assertEquals(
            "A muscle with no region cannot be reached from the picker. If a new " +
                "one genuinely is not a body location, add it beside " +
                "CARDIOVASCULAR_SYSTEM and give it a chip.",
            listOf(Muscle.CARDIOVASCULAR_SYSTEM),
            unplaced,
        )
    }

    @Test
    fun `every region has at least one muscle`() {
        val empty = BodyRegion.entries.filter { it.muscles.isEmpty() }
        assertTrue("These regions highlight but return nothing: $empty", empty.isEmpty())
    }

    @Test
    fun `every region is drawn in at least one view`() {
        val invisible = BodyRegion.entries.filter { it.views.isEmpty() }
        assertTrue("Unreachable regions: $invisible", invisible.isEmpty())
    }

    @Test
    fun `both views have regions, and each view's set is what the artwork must draw`() {
        val front = BodyRegion.forView(BodyView.FRONT)
        val back = BodyRegion.forView(BodyView.BACK)
        assertTrue(front.isNotEmpty() && back.isNotEmpty())

        // Regions on both sides are the ones a selection must survive turning
        // the body around; if this set empties, the cross-fade has nothing to
        // keep highlighted and the interaction changes meaning.
        val both = front.intersect(back.toSet())
        assertTrue("Expected some regions on both views, got $both", both.isNotEmpty())
    }

    @Test
    fun `svg ids are stable, lowercase and hyphenated`() {
        BodyRegion.entries.forEach { region ->
            assertTrue(
                "'${region.svgId}' must match the id used in the artwork",
                region.svgId.matches(Regex("[a-z]+(-[a-z]+)*")),
            )
        }
        assertEquals("lower-legs", BodyRegion.LOWER_LEGS.svgId)
        assertEquals("hip-flexors", BodyRegion.HIP_FLEXORS.svgId)
    }

    @Test
    fun `no two regions share an svg id`() {
        val ids = BodyRegion.entries.map { it.svgId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `selecting a region covers every synonym of its muscles`() {
        // The picker selects regions but the filter matches muscles, so a region
        // must carry both spellings of anything the dataset names twice.
        val chest = BodyRegion.CHEST.muscles
        assertTrue(Muscle.PECTORALS in chest && Muscle.CHEST in chest)

        val abs = BodyRegion.ABS.muscles
        assertTrue(Muscle.ABS in abs && Muscle.ABDOMINALS in abs)

        val quads = BodyRegion.QUADS.muscles
        assertTrue(Muscle.QUADS in quads && Muscle.QUADRICEPS in quads)
    }
}
