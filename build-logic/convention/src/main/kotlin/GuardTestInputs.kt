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
    val guardedDirs = listOf(
        "src/main/res",
        "src/main/assets",
        "schemas",
        // BaselineProfileGuardTest reads the committed profile through
        // java.io.File. It is generated into a variant source set rather than
        // src/main, so none of the entries above cover it.
        "src/placeholderRelease/generated/baselineProfiles",
    )

    // Whole directories cover most guards, but the manifest is a single file and
    // its parent holds the entire source tree; declaring that as an input would
    // make every test task rerun on any source edit.
    val guardedFiles = listOf(
        "src/main/AndroidManifest.xml",
        // TermResourceParityTest reads this from core:exercise-data, across a
        // module boundary. Relative paths out of the module are exactly as
        // invisible to Gradle as the ones inside it, and being someone else's
        // file makes it likelier to change without this task rerunning.
        "../model/src/test/resources/dataset-vocabulary.json",
        // SchemaDumpGuardTest in core:ai asserts that tools/gemini-schema.json
        // is still the schema the app sends, because that file is what the
        // Gemini probe scripts put in front of the live endpoint. Caught by
        // this rule doing its job: corrupting the JSON left the task
        // UP-TO-DATE and the build green.
        "../../tools/gemini-schema.json",
    )

    declareRepoWideGuardInputs()

    tasks.withType<Test>().configureEach {
        // `SchemaDumpGuardTest` documents `-Drepforth.regenerate=true` as the way
        // to rewrite the file it guards. That flag reaches the Gradle daemon and
        // stops there — the test runs in a forked JVM that never saw it — so the
        // documented command silently did nothing but rerun the failing
        // assertion. Forwarded here rather than in the test, which cannot reach
        // across the fork to fetch it.
        systemProperty("repforth.regenerate", providers.systemProperty("repforth.regenerate").getOrElse("false"))

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

/**
 * The one guard that reads the whole repository rather than one file.
 *
 * `NetworkBoundaryTest` in `:app` asserts that only `core:ai` and `core:media`
 * declare an HTTP client, and that only two files make calls with it. Since §8
 * was amended that is a scope control rather than a cleartext one — it keeps
 * "where does this app talk out, and why" answerable — but it still only means
 * anything if nothing can go around it.
 *
 * Most of what it reads already invalidates this task through the compile
 * classpath: `:app` depends, directly or through a feature, on every module
 * that exists today. The gap is a module `:app` does *not* depend on adding an
 * HTTP client, which is exactly the case the guard is for and exactly the case
 * the classpath would not notice. So the build files are declared explicitly.
 *
 * Scoped to `:app` because that is where the test lives; every other module
 * would pay for an input it never reads.
 */
private fun Project.declareRepoWideGuardInputs() {
    if (path != ":app") return

    tasks.withType<Test>().configureEach {
        inputs.files(
            rootProject.layout.projectDirectory.asFileTree.matching {
                include("**/build.gradle.kts")
                exclude("**/build/**")
                exclude("**/.gradle/**")
            },
        )
            .withPropertyName("guardedFiles-module-build-files")
            .withPathSensitivity(PathSensitivity.RELATIVE)

        // `MotionTokenTest` reads every module's Kotlin sources, for the same
        // reason and with the same gap: most of them reach this task through
        // the compile classpath, but a module `:app` does not depend on could
        // add an unswitchable animation and nothing would rerun. Sources are
        // usually the case that needs no declaring; read across a module
        // boundary through `java.io.File`, they are exactly the case that does.
        inputs.files(
            rootProject.layout.projectDirectory.asFileTree.matching {
                include("**/src/main/**/*.kt")
                exclude("**/build/**")
                exclude("**/.gradle/**")
                exclude("design-system/**")
            },
        )
            .withPropertyName("guardedFiles-module-sources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
