plugins {
    alias(libs.plugins.cadence.android.library)
    alias(libs.plugins.cadence.android.compose)
}

android {
    namespace = "app.cadence.core.ui"
}

dependencies {
    implementation(project(":core:domain"))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.biometric)
    api(libs.androidx.fragment.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
