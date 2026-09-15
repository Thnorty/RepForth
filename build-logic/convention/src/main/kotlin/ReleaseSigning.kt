import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * The release signing key, when the machine building has one.
 *
 * Both application modules shipped unsigned release output until 1.0.1, and
 * `apksigner` with the SDK's debug keystore was how a release build reached a
 * personal device. That keystore is not a signature: it ships with the SDK and
 * its password is published, so anyone can produce an APK the installer treats
 * as the same app. It is also **sticky** — Android refuses an update signed by a
 * different key, so every device that took a debug-signed build has to uninstall
 * before it can take a real one, losing its plans and history on the way.
 *
 * ## Where the key lives
 *
 * `.repforth/signing.properties` in the repository root, naming a keystore
 * beside it. The file declares `storeFile`, `storePassword`, `keyAlias` and
 * `keyPassword`; a relative `storeFile` resolves against the folder the
 * properties file is in, so the pair backs up and restores as one directory.
 *
 * **It was outside the working tree until the owner asked for it here**, and
 * that is a real trade. The repository is public, and a key committed once is a
 * key that has to be replaced with every existing install orphaned — so being
 * ignored is no longer a convenience, it is the only thing standing between the
 * key and a `git add -A`. Three things enforce it: `/.repforth/` in the root
 * `.gitignore`, `.repforth/.gitignore` excluding the folder's contents from
 * inside it so an edit to the root file cannot expose them, and
 * [requireIgnoredByGit] below, which fails the build rather than signing with a
 * key git can see.
 *
 * ## Absent is not an error
 *
 * A machine without the key — CI, a contributor's clone, a fresh checkout —
 * builds release output unsigned, exactly as before. Failing the build instead
 * would mean every pull request needed the maintainer's private key to compile,
 * which is the opposite of what a public repository wants.
 *
 * The consequence is worth stating plainly, because it is quiet: an unsigned
 * release APK cannot be installed. The filename is the tell. If a release build
 * will not install, check whether the output is `…-release-unsigned.apk` before
 * looking anywhere else.
 */
internal fun Project.configureReleaseSigning(extension: ApplicationExtension) {
    val credentials = releaseSigningCredentials() ?: run {
        logger.lifecycle(
            "No release signing key at $SIGNING_PROPERTIES; release output will be unsigned.",
        )
        return
    }

    requireIgnoredByGit(credentials.storeFile)
    requireIgnoredByGit(credentials.propertiesFile)

    with(extension) {
        signingConfigs.create("release") {
            storeFile = credentials.storeFile
            storePassword = credentials.storePassword
            keyAlias = credentials.keyAlias
            keyPassword = credentials.keyPassword
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

private class ReleaseSigningCredentials(
    val propertiesFile: File,
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * Reads the key, or null when this machine does not have one.
 *
 * Null for a missing file, a missing keystore, and a properties file that does
 * not declare all four values. A half-configured signing config is worse than
 * none: Gradle accepts it and the failure arrives later as an unhelpful error
 * out of `apksigner`.
 */
private fun Project.releaseSigningCredentials(): ReleaseSigningCredentials? {
    val properties = rootDir.resolve(SIGNING_PROPERTIES)
    if (!properties.isFile) return null

    val values = Properties().apply { properties.inputStream().use(::load) }
    val store = values.getProperty("storeFile")?.let { declared ->
        // Relative to the properties file, so the key and its password can be
        // backed up and restored as one folder rather than two paths that have
        // to agree.
        File(declared).takeIf { it.isAbsolute } ?: File(properties.parentFile, declared)
    }
    val storePassword = values.getProperty("storePassword")
    val alias = values.getProperty("keyAlias")
    val keyPassword = values.getProperty("keyPassword")

    if (store == null || !store.isFile || storePassword == null || alias == null || keyPassword == null) {
        logger.warn("$SIGNING_PROPERTIES is incomplete; release output will be unsigned.")
        return null
    }
    return ReleaseSigningCredentials(properties, store, storePassword, alias, keyPassword)
}

/**
 * Refuses to sign with a secret git is not ignoring.
 *
 * The key sits in the working tree, so the ignore rules are the only thing
 * keeping it out of a public repository — and an ignore rule is a line in a file
 * anyone can edit, in a project where `git add -A` is the normal way to stage.
 * This turns a silent leak into a failed build, at the one moment the key is
 * definitely being used.
 *
 * **A machine with no git answers nothing, and that is not a failure.** An
 * exported source tree has no repository to ask, and refusing to build there
 * would be inventing a dependency on a tool that has nothing to do with signing.
 * Only a clear "git can see this" fails.
 */
private fun Project.requireIgnoredByGit(secret: File) {
    // `providers.exec` rather than `ProcessBuilder`: the configuration cache
    // refuses an external process started during configuration, and it is right
    // to -- a build whose result depends on an unrecorded command cannot be
    // replayed. This goes through Gradle, so the call is an input like any other.
    val exit = runCatching {
        providers.exec {
            commandLine("git", "check-ignore", "-q", secret.absolutePath)
            workingDir = rootDir
            isIgnoreExitValue = true
        }.result.get().exitValue
    }.getOrNull() ?: run {
        logger.warn("Could not ask git whether ${secret.name} is ignored; not checking.")
        return
    }

    // 0 is ignored, 1 is not ignored, and anything else is git declining to
    // answer -- not a repository, a broken index. Only the clear "not ignored"
    // is treated as the leak.
    if (exit == 1) {
        throw GradleException(
            "${secret.absolutePath} is a signing secret and git is not ignoring it. " +
                "Restore the /.repforth/ rule in .gitignore before building a release.",
        )
    }
}

private const val SIGNING_PROPERTIES = ".repforth/signing.properties"
