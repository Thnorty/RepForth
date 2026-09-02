import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Screenshot tests, on the JVM, for a module that draws something.
 *
 * Applied on top of the compose convention by any module with a screen worth
 * looking at. It is a plugin rather than four lines in each `build.gradle.kts`
 * for the usual reason — a module declares what it is, not how the toolchain is
 * configured — and because two of the four lines are easy to get subtly wrong.
 *
 * **Why this exists at all.** Nine defects in this project have been found on a
 * device and by nothing else, and most were a layout that could not hold its own
 * text: pills that wrapped inside themselves, a row whose two halves ran past
 * each other, a Save button behind the keyboard. Unit tests cannot see any of
 * that, instrumentation tests only see it if a person thought to assert on it,
 * and both were passing the whole time. A rendered image is the only artefact
 * that disagrees.
 *
 * It runs through Robolectric rather than on a device on purpose. The matrix
 * that matters here is English against Turkish and 1x against 2x font scale —
 * `AGENTS.md` requires both — and running four renders of every screen on
 * hardware is minutes per change and a device that has to be plugged in. On the
 * JVM it is part of `./gradlew test`.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<LibraryExtension> {
                // Robolectric reads the merged resources: without this every
                // `stringResource` in a screenshot resolves to nothing and the
                // images are of an app with no words in it.
                testOptions.unitTests.isIncludeAndroidResources = true
            }

            dependencies {
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("roborazzi"))
                add("testImplementation", libs.library("roborazzi-compose"))
                add("testImplementation", libs.library("roborazzi-junit-rule"))
                add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
                // The empty activity a ComposeTestRule hosts its content in.
                // Without it Robolectric has no launcher activity to resolve and
                // every test dies before it draws anything.
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
            }

            tasks.withType<Test>().configureEach {
                // Debug only. `./gradlew test` runs the release unit tests too,
                // and the host activity above arrives through
                // `debugImplementation` -- so the release copy of every
                // screenshot test failed with no launcher activity to resolve.
                // Running them twice was never wanted regardless: a rendered
                // composable does not differ between variants, and these are the
                // slowest tests in the suite.
                if (name.contains("Release")) {
                    exclude("**/*ScreenshotTest*")
                    // The accessibility tests host a screen the same way and
                    // fail the same way without it.
                    exclude("**/*AccessibilityTest*")
                }

                // The goldens are read through `java.io.File` at runtime, so
                // Gradle cannot see them -- the same blind spot
                // `configureGuardTestInputs` exists for, and it bites the same
                // way. Deleting every golden and re-running reported UP-TO-DATE
                // and wrote nothing, which on a verify run would mean a changed
                // golden silently never being compared.
                val goldens = layout.projectDirectory.dir("src/test/screenshots")
                if (goldens.asFile.isDirectory) {
                    inputs.dir(goldens)
                        .withPropertyName("screenshotGoldens")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                }

                // Recording is opt-in per run: `-Proborazzi.record=true` rewrites
                // every golden, and a build that did that by default would make
                // the guard agree with whatever it was just shown.
                systemProperty(
                    "roborazzi.test.record",
                    providers.gradleProperty("roborazzi.record").getOrElse("false"),
                )
                systemProperty(
                    "roborazzi.test.verify",
                    providers.gradleProperty("roborazzi.record")
                        .map { "false" }
                        .getOrElse("true"),
                )
            }
        }
    }
}
