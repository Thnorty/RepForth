plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.core.exercisedata"
}

dependencies {
    // The one door to the catalog. Features depend on this, never on
    // core:database — a DAO in a feature module is a storage detail escaping.
    api(project(":core:model"))
    implementation(project(":core:database"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
