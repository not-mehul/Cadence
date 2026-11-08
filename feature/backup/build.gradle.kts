plugins {
    alias(libs.plugins.cadence.android.feature)
}

android {
    namespace = "app.cadence.feature.backup"
}

dependencies {
    implementation(libs.androidx.activity.compose)

    // The clinician PDF is drawn with the native PdfDocument, so it's tested on a device.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
}
