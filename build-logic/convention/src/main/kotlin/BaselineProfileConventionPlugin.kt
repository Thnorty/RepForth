import androidx.baselineprofile.gradle.consumer.BaselineProfileConsumerExtension
import androidx.baselineprofile.gradle.producer.BaselineProfileProducerExtension
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/*
 * Baseline profiles (§19, Phase 3), in the two halves the AndroidX plugin
 * models them as.
 *
 * A baseline profile is a list of the methods and classes worth compiling ahead
 * of time. Android applies it at install, so the first launch runs compiled
 * code instead of interpreting it. It is the only item on Phase 3's list that
 * changes nothing visible and is measured only in milliseconds someone did not
 * spend waiting.
 *
 * Two things about generating one are worth stating before anyone runs it:
 *
 *   - **It needs a real device or an emulator.** The profile is recorded by
 *     driving the app through a startup journey and watching which code runs,
 *     which cannot be done on the JVM. CI has no device, so this is a manual
 *     step whose output is committed — the same arrangement as the screenshot
 *     goldens.
 *   - **It installs and uninstalls the app repeatedly.** On a phone with real
 *     data in it, that data is gone. `AGENTS.md` already carries this warning
 *     for `connectedAndroidTest`; it applies here for the same reason. The
 *     managed virtual device below exists so that nobody has to weigh that.
 */

/**
 * The app that consumes a profile.
 *
 * Adds the `benchmark` build type the generator measures against: release
 * code, debug signing. Release-like is the point — a profile recorded against a
 * debuggable build describes a program the user never runs.
 */
class BaselineProfileConsumerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.baselineprofile")

        extensions.configure<ApplicationExtension> {
            buildTypes {
                create("benchmark") {
                    initWith(getByName("release"))
                    // Signed with the debug key because the point is to measure
                    // release code, not to distribute it, and the release
                    // signing key is deliberately not in this repository (§18).
                    signingConfig = signingConfigs.getByName("debug")
                    // Without this the benchmark variant cannot resolve its
                    // dependencies on modules that only know about `release`.
                    matchingFallbacks += listOf("release")
                }
            }
        }

        extensions.configure<BaselineProfileConsumerExtension> {
            // Committed, not generated during the build. Generating needs a
            // device and CI has none, so a build-time profile would simply be
            // absent on every machine without an emulator -- and absent
            // silently, which is the failure this repo keeps writing guards
            // against. Checked in instead, and reviewable in a diff, exactly
            // like the screenshot goldens.
            saveInSrc = true
            automaticGenerationDuringBuild = false
        }

        dependencies {
            // The profile in the APK does nothing on its own before API 31 --
            // this is what installs it at first launch on 28 through 30, which
            // is most of the range §4 supports.
            add("implementation", libs.library("androidx-profileinstaller"))
        }
    }
}

/**
 * The test module that produces a profile.
 *
 * `com.android.test` rather than a library: it builds its own APK and drives
 * the app under test from outside the process, which is the only way to observe
 * a cold start.
 */
class BaselineProfileProducerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("androidx.baselineprofile")

        extensions.configure<TestExtension> {
            configureKotlinAndroid(this)

            // Without this the test APK has no runner, no test is discovered,
            // and the whole generation reports BUILD SUCCESSFUL having produced
            // nothing -- which is exactly what happened the first time. The
            // only visible symptom was a warning saying no rules were
            // generated, in a wall of Gradle output.
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            // The `media` dimension belongs to
            // `AndroidApplicationConventionPlugin`, so its counterpart belongs
            // here rather than in the module: a test module targeting an app
            // with flavours must say which one, and `placeholder` is the
            // flavour §20 says the public source must build.
            defaultConfig.missingDimensionStrategy("media", "placeholder")

            // Declared in ManagedDevice.kt, which `core:database` also uses
            // to run its migrations. One device, described once -- otherwise
            // the profile could end up recorded on one API level and the
            // migrations proven on another, with nothing to say so.
            configureManagedDevice()
        }

        extensions.configure<BaselineProfileProducerExtension> {
            // The emulator defined above, not whatever happens to be plugged
            // in. Generating installs and uninstalls the app repeatedly, and
            // uninstalling takes the user's plans and history with it -- so
            // recording on a real phone has to be asked for, never defaulted
            // to. The device name is declared here too, so the choice and the
            // thing chosen do not live in two files.
            managedDevices += MANAGED_DEVICE
            useConnectedDevices = false
        }

        dependencies {
            add("implementation", libs.library("androidx-test-junit"))
            add("implementation", libs.library("androidx-test-uiautomator"))
            add("implementation", libs.library("androidx-benchmark-macro-junit4"))
        }
    }
}
