package com.repforth.core.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture's own contract, because two of the six it replaced got it wrong.
 *
 * A fake is only useful while it is at least as strict as the thing it stands
 * in for. These are not tests of a feature — they are the three promises
 * [FakeProfiles] makes to every module that now shares it, and each one is a
 * defect that was really present in one of the copies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeProfilesTest {

    /**
     * **The one that could hide a real bug.**
     *
     * One copy built a new `MutableStateFlow` inside `observeProfile()`, so a
     * collector held the value from the moment it subscribed and never saw a
     * later save. A view model that ignored a profile change would have passed
     * against it, because nothing downstream could tell the difference between
     * "did not react" and "was never told".
     */
    @Test
    fun `a collector sees a save that happens after it subscribes`() = runTest {
        val profiles = FakeProfiles()
        val seen = mutableListOf<String?>()

        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            profiles.observeProfile().collect { seen += it?.id }
        }

        profiles.save(sampleProfile(id = "after"))
        runCurrent()
        collector.cancel()

        // The null is the empty fixture the collector subscribed to; the point
        // is the second element, which a per-call flow would never deliver.
        assertEquals(listOf(null, "after"), seen)
    }

    /**
     * The other copies made `save` a no-op, so a profile written during a test
     * was not the profile read back — the read silently answered with whatever
     * the fixture was seeded with.
     */
    @Test
    fun `a saved profile is the profile read back`() = runTest {
        val profiles = FakeProfiles(sampleProfile(id = "before"))

        profiles.save(sampleProfile(id = "after"))

        assertEquals("after", profiles.getProfile()?.id)
        assertEquals("after", profiles.observeProfile().first()?.id)
    }

    /** Writes are recorded in order, for the tests that assert on them. */
    @Test
    fun `every save is recorded`() = runTest {
        val profiles = FakeProfiles()

        profiles.save(sampleProfile(id = "first"))
        profiles.save(sampleProfile(id = "second"))

        assertEquals(listOf("first", "second"), profiles.saved.map { it.id })
    }

    @Test
    fun `deleting leaves nothing behind`() = runTest {
        val profiles = FakeProfiles(sampleProfile())

        profiles.deleteAll()

        assertNull(profiles.getProfile())
        assertNull(profiles.observeProfile().first())
    }

    /** The seam the older copies exposed as a field, kept so they could be dropped. */
    @Test
    fun `the profile can be set without going through save`() = runTest {
        val profiles = FakeProfiles()

        profiles.profile = sampleProfile(id = "seeded")

        assertEquals("seeded", profiles.getProfile()?.id)
        assertTrue(profiles.saved.isEmpty())
    }
}
