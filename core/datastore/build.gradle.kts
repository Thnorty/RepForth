plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.core.datastore"
}

dependencies {
    // Preferences map to core:model types; nothing maps the other way.
    api(project(":core:model"))

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
