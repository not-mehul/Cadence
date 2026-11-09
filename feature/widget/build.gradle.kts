plugins {
    alias(libs.plugins.cadence.android.feature)
}

android {
    namespace = "app.cadence.feature.widget"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
