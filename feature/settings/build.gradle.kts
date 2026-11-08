plugins {
    alias(libs.plugins.cadence.android.feature)
}

android {
    namespace = "app.cadence.feature.settings"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
