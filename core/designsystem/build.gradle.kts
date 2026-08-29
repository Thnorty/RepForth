plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.compose)
}

android {
    namespace = "com.repforth.core.designsystem"
}

dependencies {
    // The body map speaks in BodyRegion, so the design system needs the domain
    // vocabulary. `api` because callers hand those types in and read them back.
    api(project(":core:model"))

    // `api`, not `implementation`: this module is the single door to Compose for
    // every consumer, so no feature module ever re-declares the Compose stack.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
}
