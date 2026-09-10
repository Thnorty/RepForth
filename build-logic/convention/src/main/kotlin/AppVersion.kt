/**
 * What this release is called, named once for both artifacts.
 *
 * The phone and the watch ship as one listing (§11), so they must agree about
 * the version the user sees — and until now each declared it in its own
 * `build.gradle.kts`, which is the shape AGENTS.md opens by calling a bug. Two
 * copies of a number that must match is a number that will eventually not
 * match, and the symptom would be a store listing whose two artifacts disagree
 * about what release they belong to.
 */
const val APP_VERSION_NAME = "1.0.0"

/**
 * The phone's version code.
 *
 * §11 requires the two artifacts to have *different* codes, and they are 1000
 * apart rather than adjacent so the watch's number cannot be mistaken for the
 * next phone release.
 */
const val PHONE_VERSION_CODE = 1

/** The watch's, kept a clear distance from the phone's. See above. */
const val WEAR_VERSION_CODE = PHONE_VERSION_CODE + 1000
