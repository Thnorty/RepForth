plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.core.userdata"
}

dependencies {
    // The one door to user data, mirroring core:exercise-data for the catalog.
    // Features depend on this; nothing outside it sees a DAO.
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    api(project(":core:workout"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
