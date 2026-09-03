package com.repforth.wear

import com.repforth.core.testing.StringParityContract

/**
 * The watch's strings, in both languages.
 *
 * §13 makes neither language a translation of the other and requires them to
 * ship together, and a wrist is where a missing string is least survivable:
 * there is no surrounding context to infer it from and no room for a fallback.
 */
class WearStringParityTest : StringParityContract()
