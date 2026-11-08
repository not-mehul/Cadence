plugins {
    alias(libs.plugins.cadence.android.application)
    alias(libs.plugins.cadence.android.compose)
    alias(libs.plugins.cadence.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.cadence"

    defaultConfig {
        applicationId = "app.cadence"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to a release build on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 and release signing arrive with the sideload pipeline in Phase 7.
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:log"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:today"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:reminders"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.androidx.room.runtime)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
