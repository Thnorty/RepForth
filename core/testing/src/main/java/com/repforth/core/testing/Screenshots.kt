package com.repforth.core.testing

/*
 * The settings every screenshot test shares.
 *
 * Constants rather than a base class: Roborazzi's rule and Robolectric's
 * annotations have to be declared on the test itself, so the only thing worth
 * hoisting is the handful of values that must agree across modules. A device or
 * an SDK that differed between two modules would produce goldens that could not
 * be compared to each other, and nothing would say so.
 *
 * Plain strings and ints on purpose — this module must stay free of Roborazzi
 * and Robolectric, because it is on the `api` surface of everything that
 * consumes it.
 */

/**
 * The API level Robolectric renders at.
 *
 * Below `compileSdk` deliberately. Robolectric ships one `android-all` jar per
 * released API and lags the newest by months; pinning to the newest thing the
 * app compiles against would break the build on an SDK bump for a reason that
 * has nothing to do with the app. What is being tested is layout, and layout
 * does not change between 34 and 36.
 */
const val SCREENSHOT_SDK = 34

/**
 * A 411x891dp phone at 420dpi — a Pixel 5, which is close to the Galaxy S23
 * these defects were found on.
 *
 * Spelled as a qualifier rather than taken from `RobolectricDeviceQualifiers`
 * so this module needs no Roborazzi dependency. The width is what matters: it
 * is the number that decides whether four chips fit on one line.
 */
const val SCREENSHOT_DEVICE =
    "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/**
 * Robolectric's qualifiers for the two languages this app ships.
 *
 * Both are named, including the default. A screenshot test that set a qualifier
 * only when it wanted something other than English rendered in whatever the
 * previous test in the same JVM had left behind — which passed when the class
 * ran alone and failed when the suite ran in a different order.
 */
const val ENGLISH = "en-rUS"
const val TURKISH = "tr-rTR"

/**
 * Where a golden lives, relative to the module being tested.
 *
 * One directory per module rather than one for the repo: a screenshot belongs
 * beside the screen it is of, and a module that is deleted should take its
 * goldens with it.
 */
fun screenshotPath(name: String): String = "src/test/screenshots/$name.png"
