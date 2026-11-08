plugins {
    alias(libs.plugins.cadence.android.feature)
}

android {
    namespace = "app.cadence.feature.onboarding"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(project(":feature:backup"))
}
