package com.repforth.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces string resource parity between English and Turkish.
 *
 * RepForth ships English and Turkish as equal first-class languages in lockstep.
 * Neither is a translation or a fallback of the other; every user-visible string
 * must exist in both `values/strings.xml` and `values-tr/strings.xml`.
 */
class StringParityTest {

    private val englishFile = File("src/main/res/values/strings.xml")
    private val turkishFile = File("src/main/res/values-tr/strings.xml")

    private fun extractKeys(file: File): List<String> {
        assertTrue(
            "Expected ${file.absolutePath} to exist; is the unit test running from the module dir?",
            file.exists(),
        )
        return STRING_KEY_REGEX
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toList()
    }

    private fun findDuplicates(keys: List<String>): Set<String> {
        return keys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
    }

    @Test
    fun `every english key has a turkish translation`() {
        val englishKeys = extractKeys(englishFile).toSet() - INTENTIONAL_EXCEPTIONS
        val turkishKeys = extractKeys(turkishFile).toSet()
        val missingInTurkish = englishKeys - turkishKeys

        assertTrue(
            "Missing Turkish translation for: $missingInTurkish",
            missingInTurkish.isEmpty(),
        )
    }

    @Test
    fun `every turkish key exists in the english strings`() {
        val englishKeys = extractKeys(englishFile).toSet()
        val turkishKeys = extractKeys(turkishFile).toSet()
        val missingInEnglish = turkishKeys - englishKeys

        assertTrue(
            "Found Turkish keys with no English counterpart (typo or stale key): $missingInEnglish",
            missingInEnglish.isEmpty(),
        )
    }

    @Test
    fun `neither strings file declares duplicate keys`() {
        val englishDuplicates = findDuplicates(extractKeys(englishFile))
        val turkishDuplicates = findDuplicates(extractKeys(turkishFile))

        assertTrue(
            "Duplicate keys found in English strings: $englishDuplicates",
            englishDuplicates.isEmpty(),
        )
        assertTrue(
            "Duplicate keys found in Turkish strings: $turkishDuplicates",
            turkishDuplicates.isEmpty(),
        )
    }

    @Test
    fun `every intentional exception exists in english strings`() {
        val englishKeys = extractKeys(englishFile).toSet()
        val staleExceptions = INTENTIONAL_EXCEPTIONS - englishKeys

        assertTrue(
            "Stale entries in INTENTIONAL_EXCEPTIONS that no longer exist in English strings: $staleExceptions",
            staleExceptions.isEmpty(),
        )
    }

    companion object {
        private val STRING_KEY_REGEX = Regex("""<string\b[^>]*\bname="([^"]+)"""")

        // "app_name" is a brand name and is deliberately not translated.
        // Adding any new key to this set must be a deliberate decision.
        private val INTENTIONAL_EXCEPTIONS = setOf("app_name")
    }
}
