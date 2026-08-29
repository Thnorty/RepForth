package com.repforth.app

import com.repforth.core.testing.StringParityContract

/**
 * The app module's strings, held to the shared parity contract.
 *
 * The checks themselves live in `core:testing` because every module with strings
 * needs them, not just this one.
 */
class StringParityTest : StringParityContract() {

    // "app_name" is a brand name and is deliberately not translated. Adding
    // anything here must be a deliberate decision, not a way past a red test.
    override val intentionalExceptions = setOf("app_name")
}
