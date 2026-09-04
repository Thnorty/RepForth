import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applied by a library module whose tests need a real Android runtime.
 *
 * Two do: `core:database`, whose migrations are statements about what SQLite
 * does to a file, and `core:secrets`, whose keystore-backed storage does not
 * exist off-device. Everything else in this project is tested on the JVM and
 * should stay that way.
 *
 * It supplies two things those modules were each supplying for themselves, or
 * not at all:
 *
 * **The runner.** Both modules set `testInstrumentationRunner` in their own
 * build files, which is toolchain configuration in the one place this project
 * says it must never live. A module that forgot it would not fail — it would
 * discover no tests and report success, which is the same silent no-op the
 * baseline profile guard exists for.
 *
 * **A device to run on.** This is the change that matters. Without a managed
 * device the only target is whatever is plugged in, and
 * `connectedAndroidTest` **uninstalls the app under test when it finishes,
 * taking its files with it** — on the maintainer's phone that means their plans
 * and their training history. So the migration tests were written, committed,
 * and then never run once, for months, because running them meant choosing
 * between proving the migration and keeping the data the migration exists to
 * protect. They also could not run in CI, which has no phone.
 *
 * With [MANAGED_DEVICE] declared here they run on an emulator, on any machine,
 * and in CI:
 *
 * ```
 * ./gradlew :core:database:pixel6Api34DebugAndroidTest
 * ```
 *
 * `connectedAndroidTest` still exists and still points at hardware. Prefer the
 * managed device unless the phone is the thing being tested.
 */
class AndroidInstrumentationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.configure<LibraryExtension> {
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            configureManagedDevice()
        }
    }
}
