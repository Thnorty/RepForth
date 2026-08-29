import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * Declares the files that guard tests read at runtime as inputs of the test task.
 *
 * This repo has a deliberate pattern: where the platform forces a value to be
 * duplicated outside Kotlin, a test reads the other copy off disk and asserts
 * they agree. `LaunchBackgroundTest` reads `colors.xml`, `StringParityTest`
 * reads both `strings.xml`, `SchemaExportTest` reads the exported Room schema,
 * `BackupPolicyTest` reads the manifest.
 *
 * Those reads happen through `java.io.File` at runtime, so Gradle cannot see
 * them. Without this, editing `values-tr/strings.xml` and running the tests
 * reports UP-TO-DATE and passes — the guard silently does not run, precisely
 * when it is needed. Verified: before this existed, deleting a Turkish string
 * left the test task up to date and the build green.
 *
 * Source files need no such treatment; editing one recompiles, which changes the
 * test runtime classpath and invalidates the task on its own.
 */
internal fun Project.configureGuardTestInputs() {
    val guardedDirs = listOf("src/main/res", "src/main/assets", "schemas")

    // Whole directories cover most guards, but the manifest is a single file and
    // its parent holds the entire source tree; declaring that as an input would
    // make every test task rerun on any source edit.
    val guardedFiles = listOf("src/main/AndroidManifest.xml")

    tasks.withType<Test>().configureEach {
        guardedDirs.forEach { path ->
            val dir = layout.projectDirectory.dir(path)
            if (dir.asFile.isDirectory) {
                inputs.dir(dir)
                    .withPropertyName("guardedFiles-${path.replace('/', '-')}")
                    // RELATIVE, not ABSOLUTE: the build cache must still hit
                    // when the same content is checked out at a different path,
                    // which is the normal case on CI.
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
        }
        guardedFiles.forEach { path ->
            val file = layout.projectDirectory.file(path)
            if (file.asFile.isFile) {
                inputs.file(file)
                    .withPropertyName("guardedFile-" + path.replace('/', '-'))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
        }
    }
}
