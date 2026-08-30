plugins {
    alias(libs.plugins.repforth.android.library)
}

android {
    namespace = "com.repforth.core.testing"
}

dependencies {
    // `api`, not `implementation`: the contract classes here expose JUnit
    // annotations to whichever module subclasses them, so a consumer that only
    // declared this module would not compile without it.
    api(libs.junit)

    // FakePreferencesStore implements DataStore, so consumers see those types.
    api(libs.androidx.datastore.preferences)

    // InMemorySecretStore implements SecretStore, for the same reason.
    //
    // This module must never reach androidTest: it exposes JUnit with `api`,
    // and pulling that into an APK fails dexing. The instrumentation tests keep
    // their own fixtures for exactly this reason.
    api(project(":core:secrets"))
}
