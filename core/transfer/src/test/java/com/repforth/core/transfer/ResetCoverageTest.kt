package com.repforth.core.transfer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Reset app" must clear every store this class holds.
 *
 * The failure this guards is silent and one-directional. A store added to
 * [DefaultDataTransfer] and forgotten here leaves data behind after the user has
 * explicitly asked for all of it to go — and nothing tells them. With provider
 * keys in the app that stops being a tidiness problem: a phone that is reset and
 * then sold or handed on still has the previous owner's API key in it, billable
 * to their account.
 *
 * Asserted on the source because the alternative is a fake for every repository
 * plus a convention that new ones get added to the test, which is the same thing
 * being forgotten one file further away. This reads the constructor, so a new
 * dependency fails the test by existing rather than by being remembered.
 *
 * Watched failing: removing `providers.deleteAll()` from `resetApp` reports the
 * dependency by name.
 */
class ResetCoverageTest {

    @Test
    fun `every store the transfer holds is cleared by reset`() {
        val source = File("src/main/java/com/repforth/core/transfer/DataTransfer.kt")
        assertTrue(
            "Expected ${source.absolutePath} to exist; is the test running from " +
                "the module directory?",
            source.exists(),
        )
        val text = source.readText()

        val dependencies = constructorPropertiesOf(text)
        assertTrue(
            "Could not read DefaultDataTransfer's constructor. If it was " +
                "renamed or reformatted, fix this test rather than deleting it.",
            dependencies.isNotEmpty(),
        )

        // resetApp calls deleteWorkoutData, so a store cleared there counts.
        val reachable = bodyOf(text, "resetApp") + bodyOf(text, "deleteWorkoutData")

        dependencies.forEach { (name, type) ->
            if (type in NOT_A_STORE) return@forEach
            assertTrue(
                "`$name` is a store held by DefaultDataTransfer but nothing in " +
                    "resetApp() or deleteWorkoutData() touches it, so \"reset " +
                    "app\" leaves its data on the device. Clear it in " +
                    "resetApp(), or add its type to NOT_A_STORE here with a " +
                    "reason.",
                name in reachable,
            )
        }
    }

    private companion object {
        /**
         * Types that hold nothing to delete.
         *
         * Deliberately a short list that has to be argued for. A clock is not a
         * store; almost everything else this class is given will be.
         */
        val NOT_A_STORE = setOf("TimeSource")

        val CONSTRUCTOR = Regex(
            """class\s+DefaultDataTransfer[^(]*\((.*?)\)\s*:""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val PROPERTY = Regex("""val\s+(\w+)\s*:\s*(\w+)""")

        fun constructorPropertiesOf(source: String): List<Pair<String, String>> {
            val parameters = CONSTRUCTOR.find(source)?.groupValues?.get(1) ?: return emptyList()
            return PROPERTY.findAll(parameters).map { it.groupValues[1] to it.groupValues[2] }
                .toList()
        }

        /** The body of one `override suspend fun`, up to its closing brace. */
        fun bodyOf(source: String, function: String): String {
            val start = source.indexOf("fun $function(")
            if (start < 0) return ""
            val open = source.indexOf('{', start)
            if (open < 0) return ""

            var depth = 0
            for (index in open until source.length) {
                when (source[index]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return source.substring(open, index + 1)
                    }
                }
            }
            return ""
        }
    }
}
