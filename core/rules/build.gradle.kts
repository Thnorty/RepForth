plugins {
    alias(libs.plugins.repforth.android.library)
}

android {
    namespace = "com.repforth.core.rules"
}

dependencies {
    // Domain types only. No database, no Android framework, no DI — this module
    // is a pure function from constraints to a plan, which is what makes §8's
    // requirement that AI output be validated against the same rules possible.
    api(project(":core:model"))

    testImplementation(libs.junit)
}
