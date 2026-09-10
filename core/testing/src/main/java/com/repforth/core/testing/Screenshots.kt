package com.repforth.core.testing

import com.github.takahirom.roborazzi.RoborazziOptions
import com.dropbox.differ.SimpleImageComparator

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
 * A 226x226dp round watch at 340dpi — the paired Galaxy Watch Ultra, measured.
 *
 * `round` is the qualifier that matters and the reason this is not simply a
 * small phone: §11's screens are centred rather than start-aligned because a
 * round display clips its corners, and a square render would let a layout that
 * loses its edges pass. `small` and `notlong` come with the shape.
 *
 * **This said 240dp at 320dpi until 2026-09-10, and no such watch exists.** The
 * numbers were assumed from the model name rather than read off the device. The
 * Ultra reports `DisplayMetrics{density=2.125, width=480, height=480}` at
 * 340dpi, which is 480 / 2.125 = 225.9dp — so every wear golden was rendering
 * about 6% wider than the wrist they were meant to describe. That is the margin
 * the clipping found in the 2026-09-09 hardware session lived in, and it is why
 * the goldens stayed green while the watch did not. Read it back with:
 *
 * ```
 * adb shell dumpsys window displays | grep DisplayMetrics
 * ```
 *
 * The two below are *not* measured devices — no watch of either description has
 * been in hand. They are deliberate bounds, and their comments say so.
 */
const val WATCH_SCREENSHOT_DEVICE =
    "w226dp-h226dp-small-notlong-round-any-340dpi-keyshidden-nonav"

/**
 * A 180x180dp round watch — a deliberate lower bound, not a measured device.
 *
 * §11 asks for "round/square screen previews", and this is the half that finds
 * the real problems: 46dp narrower than the device above, which is roughly one
 * button's worth of label. A row that fits on the Ultra and wraps here is
 * exactly the defect the phone's Turkish goldens exist to catch, one size down.
 *
 * It used to claim to be "the smallest shape still sold", which was never
 * checked against a real product. Treat it as a stress size that is at least as
 * tight as anything shipping, which is the property actually wanted here.
 */
const val WATCH_SMALL_ROUND_DEVICE =
    "w180dp-h180dp-small-notlong-round-any-320dpi-keyshidden-nonav"

/**
 * A 200x200dp square watch — again a shape to satisfy, not a device measured.
 *
 * `notround`, which changes what the layout may use rather than merely how it
 * looks: the corners are usable here and clipped everywhere else. A screen
 * tuned only for round wastes them; one tuned only for square loses content.
 */
const val WATCH_SQUARE_DEVICE =
    "w200dp-h200dp-small-notlong-notround-any-320dpi-keyshidden-nonav"

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

/**
 * How close a render has to be to its golden.
 *
 * Not exact, and the tolerance is measured rather than guessed. The goldens are
 * recorded on a maintainer's Windows machine and verified on an Ubuntu CI
 * runner, and the two do not rasterise text identically. Comparing all 31 CI
 * renders against their goldens gave: **at most 0.069% of pixels different, by
 * at most 4 of 255** — antialiasing along the edges of glyphs, invisible in the
 * side-by-side, and confirmed by an empty diff panel.
 *
 * The decision is stated as a [RoborazziOptions.CompareOptions.resultValidator]
 * because that is what actually decides. `SimpleImageComparator`'s `maxDistance`
 * alone did not: set to 0.02 of full range — above the worst 4/255 observed —
 * every golden still failed on CI, because the default validator has the last
 * word regardless of what the comparator tolerated. The comparator is kept
 * because it can only narrow what reaches the validator.
 *
 * **The margins, both ways.** 0.1% is 1.45x the worst noise measured, and a
 * real change is far larger: one word of a label at this device size is roughly
 * 0.25% of the image, and a wrapped line or a shifted row moves whole percent.
 * So this sits between the two — but not by much on the lower side, and a
 * platform whose text rendering drifts further would need this re-measured
 * rather than nudged.
 */
val SCREENSHOT_COMPARISON: RoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        imageComparator = SimpleImageComparator(maxDistance = 0.02f),
        resultValidator = { result ->
            result.pixelDifferences.toFloat() / result.pixelCount < MAX_CHANGED_FRACTION
        },
    ),
)

/** See [SCREENSHOT_COMPARISON]. Measured worst case is 0.00069. */
private const val MAX_CHANGED_FRACTION = 0.001f
