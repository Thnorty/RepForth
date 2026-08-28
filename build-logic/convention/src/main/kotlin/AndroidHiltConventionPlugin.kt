import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Applied by any module that declares or consumes injected dependencies.
 *
 * Owns the whole DI setup: the KSP processor, the Hilt Gradle plugin, and both
 * coordinates. A module that needs injection applies this plugin and writes
 * nothing else — so the Hilt version can never differ between two modules, and
 * nobody can forget the `ksp(...)` half of the pair, which fails at runtime
 * rather than at compile time.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("dagger.hilt.android.plugin")

            dependencies {
                add("implementation", libs.library("hilt-android"))
                add("ksp", libs.library("hilt-compiler"))
            }
        }
    }
}
