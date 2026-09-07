import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * Applied on top of the application or library convention by any module that
 * contains Composables. Layers only the Compose concerns, so a module that has
 * no UI never pays for the compiler plugin.
 *
 * It also carries the two things any module that renders a composable on the JVM
 * needs, whether or not it records goldens: merged resources under Robolectric,
 * and the release-variant exclusion below. Those lived in the screenshot plugin
 * until the watch needed them — and the watch is an *application*, which that
 * plugin cannot configure. Leaving them there would have meant a second copy in
 * a `build.gradle.kts`, which is the one place toolchain configuration must not
 * live.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.findByType(ApplicationExtension::class.java)?.let(::enableCompose)
            extensions.findByType(LibraryExtension::class.java)?.let(::enableCompose)

            tasks.withType<Test>().configureEach {
                // Debug only. `./gradlew test` runs the release unit tests too,
                // and the empty activity a `ComposeTestRule` hosts its content in
                // arrives through `debugImplementation` -- so the release copy of
                // any test that renders fails with no launcher activity to
                // resolve, after the whole suite has run.
                //
                // Running them twice was never wanted regardless: a rendered
                // composable does not differ between variants, and these are the
                // slowest tests in the suite.
                //
                // Name a test that uses `createComposeRule` `*ComposeTest` so it
                // lands here rather than discovering this the hard way in CI.
                if (name.contains("Release")) {
                    exclude("**/*ScreenshotTest*")
                    exclude("**/*AccessibilityTest*")
                    exclude("**/*ComposeTest*")
                }
            }
        }
    }

    private fun enableCompose(extension: CommonExtension<*, *, *, *, *, *>) {
        extension.buildFeatures.compose = true
        // Robolectric reads the merged resources: without this every
        // `stringResource` in a rendered screen resolves to nothing, and the
        // images are of an app with no words in it.
        extension.testOptions.unitTests.isIncludeAndroidResources = true
    }
}
