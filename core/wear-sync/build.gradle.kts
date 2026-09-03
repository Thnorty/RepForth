plugins {
    alias(libs.plugins.repforth.android.library)
}

android {
    namespace = "com.repforth.core.wearsync"
}

dependencies {
    // The wire types, and the running-workout types they are projected from.
    // `api` on the protocol because callers hand these types across the bridge.
    api(project(":core:wear-protocol"))
    api(project(":core:workout"))
    implementation(project(":core:model"))

    testImplementation(libs.junit)
}
