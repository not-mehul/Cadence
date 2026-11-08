plugins {
    alias(libs.plugins.cadence.android.library)
    alias(libs.plugins.cadence.android.compose)
}

android {
    namespace = "app.cadence.core.ui"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
