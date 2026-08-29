plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.repforth.core.transfer"
}

dependencies {
    // The one door to user data. This module owns the file format and nothing
    // else; what a profile or a plan *is* stays in core:model.
    implementation(project(":core:user-data"))
    implementation(project(":core:datastore"))
    // TimeSource, for stamping the export. Declared rather than relied on
    // arriving through another module.
    implementation(project(":core:common"))
    api(project(":core:model"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
