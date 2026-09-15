import com.android.build.api.dsl.ApplicationExtension
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
 * ## Where the key lives, and why it is not here
 *
 * `~/.repforth/signing.properties`, naming a keystore beside it. Outside the
 * repository on purpose: this repository is public, `.gitignore` can only stop
 * the mistakes it anticipates, and a signing key committed once is a signing key
 * that has to be replaced. Nothing in the build tree knows the password, so
 * there is no file here to leak.
 *
 * The file declares `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.
 * A relative `storeFile` resolves against the directory the properties file is
 * in, so the pair can be backed up and restored as one folder.
 *
 * ## Absent is not an error
 *
 * A machine without the key — CI, a contributor's clone, a fresh checkout —
 * builds release output unsigned, exactly as before. Failing the build instead
 * would mean every pull request needed the maintainer's private key to compile,
 * which is the opposite of what a public repository wants.
 *
 * The consequence is worth stating plainly, because it is quiet: an unsigned
 * release APK cannot be installed. If a release build will not install, check
 * whether this file was found before looking anywhere else.
 */
internal fun Project.configureReleaseSigning(extension: ApplicationExtension) {
    val credentials = releaseSigningCredentials() ?: run {
        logger.lifecycle(
            "No release signing key at $SIGNING_PROPERTIES; release output will be unsigned.",
        )
        return
    }

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
    val properties = File(System.getProperty("user.home"), SIGNING_PROPERTIES)
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
    return ReleaseSigningCredentials(store, storePassword, alias, keyPassword)
}

private const val SIGNING_PROPERTIES = ".repforth/signing.properties"
