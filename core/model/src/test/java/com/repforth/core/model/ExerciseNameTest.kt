package com.repforth.core.model

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercise names, read rather than stored.
 *
 * The dataset is entirely lower case, which is fine as data and looks like a
 * mistake in a heading. These are the shapes that actually occur in the 1,324
 * records — counted rather than imagined — plus the one that would break it
 * silently for half the app's users.
 */
class ExerciseNameTest {

    private val original = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `every word starts with a capital`() {
        assertEquals(
            "Barbell Decline Wide-Grip Press",
            exerciseDisplayName("barbell decline wide-grip press"),
        )
    }

    /** 163 of the names are hyphenated, so half-capitalising them would show. */
    @Test
    fun `a hyphen starts a word`() {
        assertEquals("3/4 Sit-Up", exerciseDisplayName("3/4 sit-up"))
    }

    /** And 143 carry a parenthesised qualifier. */
    @Test
    fun `a bracket starts a word`() {
        assertEquals(
            "Arms Overhead Full Sit-Up (Male)",
            exerciseDisplayName("arms overhead full sit-up (male)"),
        )
    }

    @Test
    fun `digits and symbols are left alone`() {
        assertEquals("45° Side Bend", exerciseDisplayName("45° side bend"))
    }

    /**
     * **The one that would have been invisible here and wrong on half the
     * devices.**
     *
     * `String.uppercase()` is locale-sensitive: in Turkish, which this app
     * ships, "i" becomes "İ". Every name is English, so "incline" would read as
     * "İncline" for a Turkish user and for nobody testing in English. The
     * character form has no such rule, and this is the assertion that keeps the
     * implementation on it.
     */
    @Test
    fun `a Turkish locale does not put a dot on the I`() {
        Locale.setDefault(Locale.forLanguageTag("tr"))

        assertEquals("Incline Hammer Curl", exerciseDisplayName("incline hammer curl"))
    }

    @Test
    fun `an empty name stays empty`() {
        assertEquals("", exerciseDisplayName(""))
    }

    /** Idempotent, so a name that has already been through does not change. */
    @Test
    fun `running it twice changes nothing`() {
        val once = exerciseDisplayName("barbell decline wide-grip press")

        assertEquals(once, exerciseDisplayName(once))
    }
}
