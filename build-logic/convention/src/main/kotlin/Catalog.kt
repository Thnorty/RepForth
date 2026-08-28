import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/*
 * Typed access to gradle/libs.versions.toml from the convention plugins.
 *
 * Plugins are compiled before the catalog's generated accessors exist, so they
 * have to look aliases up by name. These three helpers are the only place that
 * happens — a missing alias fails here with the alias in the message, rather
 * than as a NoSuchElementException from somewhere in Gradle.
 */

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.int(alias: String): Int =
    findVersion(alias)
        .orElseThrow { IllegalStateException("No version '$alias' in libs.versions.toml") }
        .requiredVersion
        .toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias)
        .orElseThrow { IllegalStateException("No library '$alias' in libs.versions.toml") }
