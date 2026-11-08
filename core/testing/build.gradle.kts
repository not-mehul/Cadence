plugins {
    alias(libs.plugins.cadence.android.library)
}

android {
    namespace = "app.cadence.core.testing"
}

// Test doubles shared by feature and app tests. Never a production dependency.
dependencies {
    api(project(":core:data"))
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.test)
    api(libs.junit4)
}
