import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applied on top of the application or library convention by any module that
 * contains Composables. Layers only the Compose concerns, so a module that has
 * no UI never pays for the compiler plugin.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.findByType(ApplicationExtension::class.java)?.let(::enableCompose)
            extensions.findByType(LibraryExtension::class.java)?.let(::enableCompose)
        }
    }

    private fun enableCompose(extension: CommonExtension<*, *, *, *, *, *>) {
        extension.buildFeatures.compose = true
    }
}
