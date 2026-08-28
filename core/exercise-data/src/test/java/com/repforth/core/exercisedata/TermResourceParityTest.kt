package com.repforth.core.exercisedata

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exercise terms are a second place §13 can break.
 *
 * `StringParityTest` guards the app module's own strings; these 88 display names
 * live here instead, are generated rather than hand-written, and are exactly the
 * kind of file someone regenerates for one locale and forgets for the other. A
 * missing Turkish term renders as a blank chip, which looks like a layout bug
 * rather than a translation gap.
 */
class TermResourceParityTest {

    private fun keys(file: File): List<String> {
        assertTrue("Expected ${file.absolutePath} to exist", file.exists())
        return KEY.findAll(file.readText()).map { it.groupValues[1] }.toList()
    }

    private val english = File("src/main/res/values/exercise_terms.xml")
    private val turkish = File("src/main/res/values-tr/exercise_terms.xml")

    @Test
    fun `both locales declare exactly the same terms`() {
        val en = keys(english).toSet()
        val tr = keys(turkish).toSet()
        assertEquals("terms missing a Turkish translation", emptySet<String>(), en - tr)
        assertEquals("Turkish terms with no English original", emptySet<String>(), tr - en)
    }

    @Test
    fun `neither locale declares a term twice`() {
        listOf(english, turkish).forEach { file ->
            val all = keys(file)
            assertEquals("duplicate keys in ${file.name}", all.size, all.toSet().size)
        }
    }

    @Test
    fun `every categorical value in the vocabulary has a term`() {
        // The generator already refuses to run without these, but the generator
        // is not what CI runs — this is.
        val declared = keys(english).toSet()
        val vocabulary = File("../model/src/test/resources/dataset-vocabulary.json").readText()
        val missing = listOf("bodyPart" to "body_part", "equipment" to "equipment", "muscle" to "muscle")
            .flatMap { (field, prefix) -> slugsFor(vocabulary, field).map { key(prefix, it) } }
            .filterNot { it in declared }

        assertEquals("categorical values with no display name", emptyList<String>(), missing)
    }

    @Test
    fun `no term was left as an untranslated copy of the key`() {
        // A generator failure mode: emitting the key as the value. It parses,
        // it renders, and it reads as `muscle_lats` on screen.
        val values = VALUE.findAll(turkish.readText())
        val leaked = values.filter { it.groupValues[1] == it.groupValues[2] }.map { it.groupValues[1] }
        assertEquals(emptyList<String>(), leaked.toList())
    }

    private fun slugsFor(json: String, field: String): List<String> {
        val block = Regex(""""$field"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(json)
        return Regex(""""([^"]+)"""").findAll(block?.groupValues?.get(1).orEmpty())
            .map { it.groupValues[1] }
            .toList()
    }

    private fun key(prefix: String, value: String): String =
        prefix + "_" + value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private companion object {
        val KEY = Regex("""<string\b[^>]*\bname="([^"]+)"""")
        val VALUE = Regex("""<string\b[^>]*\bname="([^"]+)"[^>]*>([^<]*)</string>""")
    }
}
