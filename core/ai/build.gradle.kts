plugins {
    alias(libs.plugins.repforth.android.library)
    alias(libs.plugins.repforth.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.repforth.core.ai"
}

dependencies {
    // Provider types are domain types: the settings screen reads them without
    // depending on how they are stored or which adapter speaks to the network.
    api(project(":core:model"))
    // The provider validator delegates hard constraints to the same engine that
    // builds local plans; a second implementation would drift.
    api(project(":core:rules"))

    // The two halves of a provider configuration. They are joined here and
    // nowhere else, which is what keeps the key out of DataStore.
    implementation(project(":core:datastore"))
    implementation(project(":core:secrets"))

    // §8: a direct HTTP client, not a vendor SDK. One client, two adapters.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // The adapters are tested against a real local server rather than a mocked
    // OkHttp: what is worth asserting is the bytes on the wire and what a real
    // 401 does, and a mock asserts neither.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    // FakePreferencesStore and InMemorySecretStore. The real secret store needs
    // a device, so everything above it is tested against the in-memory one.
    testImplementation(project(":core:testing"))
}
