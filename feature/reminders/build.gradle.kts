plugins {
    alias(libs.plugins.cadence.android.feature)
}

android {
    namespace = "app.cadence.feature.reminders"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
