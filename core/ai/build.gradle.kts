plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
}

android {
    namespace = "com.repforth.core.ai"
}

dependencies {
    // Provider types are domain types: the settings screen reads them without
    // depending on how they are stored or which adapter speaks to the network.
    api(project(":core:model"))

    // The two halves of a provider configuration. They are joined here and
    // nowhere else, which is what keeps the key out of DataStore.
    implementation(project(":core:datastore"))
    implementation(project(":core:secrets"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // FakePreferencesStore and InMemorySecretStore. The real secret store needs
    // a device, so everything above it is tested against the in-memory one.
    testImplementation(project(":core:testing"))
}
