package com.repforth.core.testing

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces English/Turkish string parity for whichever module subclasses it.
 *
 * RepForth ships both languages in lockstep — neither is a translation or a
 * fallback of the other — so every user-visible string must exist in both
 * `values/strings.xml` and `values-tr/strings.xml`. That rule applies to every
 * module that has strings, which is why the checks live here rather than in the
 * one module that happened to get them first: `feature:exercises` shipped
 * unguarded strings for exactly as long as this was a single file in `:app`.
 *
 * A module joins by subclassing:
 *
 * ```
 * class OnboardingStringParityTest : StringParityContract()
 * ```
 *
 * The files are read relative to the working directory, which Gradle sets to the
 * module directory, so the subclass tests its own resources and needs to say
 * nothing further. The convention plugin already declares `src/main/res` as an
 * input of every test task, so editing a strings file reruns these.
 */
abstract class StringParityContract {

    /**
     * Keys deliberately left untranslated. Override to declare them.
     *
     * Overriding is meant to be uncomfortable: an exception is a promise that a
     * word means the same thing in both languages. A separate check asserts that
     * every declared exception still exists in the English strings, so the list
     * cannot rot into a way of silencing the guard.
     */
    protected open val intentionalExceptions: Set<String> = emptySet()

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

    private fun findDuplicates(keys: List<String>): Set<String> =
        keys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys

    @Test
    fun `every english key has a turkish translation`() {
        val englishKeys = extractKeys(englishFile).toSet() - intentionalExceptions
        val turkishKeys = extractKeys(turkishFile).toSet()

        assertTrue(
            "Missing Turkish translation for: ${englishKeys - turkishKeys}",
            (englishKeys - turkishKeys).isEmpty(),
        )
    }

    @Test
    fun `every turkish key exists in the english strings`() {
        val englishKeys = extractKeys(englishFile).toSet()
        val turkishKeys = extractKeys(turkishFile).toSet()

        assertTrue(
            "Found Turkish keys with no English counterpart (typo or stale key): " +
                "${turkishKeys - englishKeys}",
            (turkishKeys - englishKeys).isEmpty(),
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
        val stale = intentionalExceptions - extractKeys(englishFile).toSet()

        assertTrue(
            "Stale entries in intentionalExceptions that no longer exist in English strings: $stale",
            stale.isEmpty(),
        )
    }

    private companion object {
        val STRING_KEY_REGEX = Regex("""<string\b[^>]*\bname="([^"]+)"""")
    }
}
