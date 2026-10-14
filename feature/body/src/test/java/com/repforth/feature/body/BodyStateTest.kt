package com.repforth.feature.body

import com.repforth.core.model.CheckIn
import com.repforth.core.model.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Body tab shows, from the stored types and check-ins. */
class BodyStateTest {

    private val types = BodyFixtures.TYPES
    private val day = BodyFixtures.TODAY

    @Test
    fun `latest value and change come from the two newest entries of that type`() {
        val state = bodyState(types, BodyFixtures.CHECK_INS)

        val weight = state.rows.single { it.type.id == "weight" }
        assertEquals(82.4, weight.latest, 1e-9)
        assertEquals(-0.7, weight.change!!, 1e-9)
    }

    /** Body fat skipped a check-in; its change is against its own previous entry. */
    @Test
    fun `a gap in one type compares with that type's previous entry`() {
        val fat = bodyState(types, BodyFixtures.CHECK_INS).rows.single { it.type.id == "body_fat" }

        assertEquals(-1.5, fat.change!!, 1e-9)
    }

    @Test
    fun `a first entry has no change`() {
        val state = bodyState(types, listOf(CheckIn("a", day, mapOf("weight" to 80.0), emptyMap())))

        assertNull(state.rows.single().change)
    }

    @Test
    fun `untracked types are hidden`() {
        val state = bodyState(types, listOf(CheckIn("a", day, mapOf("neck" to 38.0, "weight" to 80.0), emptyMap())))

        assertEquals(listOf("weight"), state.rows.map { it.type.id })
    }

    @Test
    fun `rows follow the type order`() {
        val state = bodyState(types, BodyFixtures.CHECK_INS)

        assertEquals(listOf("weight", "body_fat", "waist", "arm_left", "custom-1"), state.rows.map { it.type.id })
    }

    @Test
    fun `the latest photo of each pose is shown`() {
        val state = bodyState(types, BodyFixtures.CHECK_INS)

        assertEquals(mapOf(Pose.FRONT to "c3-front.jpg", Pose.SIDE to "c1-side.jpg"), state.latestPhotos)
    }

    @Test
    fun `compare needs two check-ins sharing a pose`() {
        assertTrue(bodyState(types, BodyFixtures.CHECK_INS).canCompare)

        val onePerPose = listOf(
            CheckIn("a", day, emptyMap(), mapOf(Pose.FRONT to "a.jpg")),
            CheckIn("b", day.minusDays(1), emptyMap(), mapOf(Pose.SIDE to "b.jpg")),
        )
        assertFalse(bodyState(types, onePerPose).canCompare)
    }

    @Test
    fun `nothing recorded is empty`() {
        assertTrue(bodyState(types, emptyList()).isEmpty)
        assertFalse(bodyState(types, BodyFixtures.CHECK_INS).isEmpty)
    }
}
