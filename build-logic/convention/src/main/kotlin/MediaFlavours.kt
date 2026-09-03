import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project

/**
 * The `media` flavour dimension, which §18 makes product-wide.
 *
 * Shared by the phone and the watch application plugins rather than written in
 * each. The point of the dimension is that a module *cannot* accidentally ship
 * licensed assets in a placeholder build, and a second copy of the definition is
 * exactly how one would come to differ — a flavour named slightly differently on
 * the watch would resolve to no matching variant, and the failure would read as
 * a dependency problem rather than a typo.
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
