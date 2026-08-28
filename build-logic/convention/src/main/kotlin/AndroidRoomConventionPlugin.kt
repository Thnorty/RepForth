import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Applied by any module that owns Room entities or DAOs.
 *
 * The schema directory is the point of this plugin. Room writes a JSON snapshot
 * of every database version into `<module>/schemas`, and those files are
 * committed. That is what makes it possible to write a real migration test
 * later (§7: never destructive migration in release builds) — without an
 * exported schema there is nothing to migrate *from*.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                add("implementation", libs.library("androidx-room-runtime"))
                add("implementation", libs.library("androidx-room-ktx"))
                add("ksp", libs.library("androidx-room-compiler"))
            }
        }
    }
}
