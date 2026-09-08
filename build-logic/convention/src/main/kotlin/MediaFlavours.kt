import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project

/**
 * The `media` flavour dimension, which §18 makes product-wide.
 *
 * Shared by the phone and the watch application plugins rather than written in
 * each. A second copy of the definition is exactly how the two would come to
 * differ — a flavour named slightly differently on the watch would resolve to no
 * matching variant, and the failure would read as a dependency problem rather
 * than a typo.
 *
 * **It gates nothing at run time, and never has.** The dimension was introduced
 * so that a module could not accidentally ship licensed assets in a placeholder
 * build; no source reads the flavour, `media-manifest.json` lives in `main`, and
 * all four variants resolve and download the same media. §6 records the owner's
 * decision that this is intended. The names are kept because removing a
 * dimension touches CI, baseline profiles and the watch module, and because a
 * build that *bundles* media would want the distinction — but nothing may be
 * inferred from them about what a build fetches.
 */
internal fun Project.configureMediaFlavours(extension: ApplicationExtension) {
    extension.apply {
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
