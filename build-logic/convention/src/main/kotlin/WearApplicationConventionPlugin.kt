import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The watch application module (§11).
 *
 * Separate from the phone's plugin for one reason that matters and one that
 * does not. The one that matters: **`minSdk` is different** — §4 sets the watch
 * baseline at Wear OS 3, API 30, against the phone's 28, and a single plugin
 * would have to be told which it was configuring anyway.
 *
 * It keeps the same `media` flavour dimension, because §18 makes that
 * product-wide rather than per-module. That is also what puts the watch into
 * CI: the build step assembles `placeholderDebug`, and a watch module with no
 * flavours would quietly not be built by it.
 */
class WearApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.minSdk = libs.int("minSdkWear")
            defaultConfig.targetSdk = libs.int("targetSdk")
            configureMediaFlavours(this)
        }
    }
}
