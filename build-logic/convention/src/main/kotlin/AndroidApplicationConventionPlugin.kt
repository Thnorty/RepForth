import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applied by phone/wear application modules.
 *
 * Owns the product-wide variant model (§18): every application module gets the
 * same `media` flavour dimension, so a module can never accidentally ship
 * licensed assets in a placeholder build.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.int("targetSdk")

            // §17's instrumentation tests run against a real Hilt graph, which
            // needs an Application the test framework builds rather than the
            // app's own. Named here rather than in the module so that a second
            // application module cannot forget it and get the puzzling failure
            // that follows — an injected field that is simply never set.
            defaultConfig.testInstrumentationRunner = "com.repforth.app.RepForthTestRunner"

            flavorDimensions += "media"
            productFlavors {
                create("placeholder") {
                    dimension = "media"
                    isDefault = true
                }
                create("licensed") {
                    dimension = "media"
                }
            }
        }
    }
}
