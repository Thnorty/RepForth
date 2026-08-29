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
}
