plugins {
    alias(libs.plugins.cadence.android.application)
    alias(libs.plugins.cadence.android.compose)
    alias(libs.plugins.cadence.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// The version comes from the release tag (v1.2.3 -> -Pcadence.version=1.2.3, see .github/workflows/release.yml).
// Local and CI builds without one are the development version.
val cadenceVersion: String = providers.gradleProperty("cadence.version").getOrElse("0.7.0")

// Release signing. The keystore lives outside the repository; CI gets it from secrets.
val keystoreFile: String? = providers.environmentVariable("CADENCE_KEYSTORE_FILE").orNull

// For checking a minified build on a device without the release key (CI smoke test): sign with the debug key.
val signWithDebugKey: Boolean =
    providers
        .gradleProperty("cadence.signWithDebugKey")
        .map {
            it.toBoolean()
        }.getOrElse(false)

android {
    namespace = "app.cadence"

    defaultConfig {
        applicationId = "app.cadence"
        versionName = cadenceVersion
        versionCode = versionCodeOf(cadenceVersion)
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("CADENCE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("CADENCE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("CADENCE_KEY_PASSWORD").get()
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    androidResources {
        // Lists the app's languages for the per-app language setting (Android 13+), from the
        // translations that exist. English is the only one so far.
        generateLocaleConfig = true
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to a release build on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // en-XA (accented, longer) and ar-XB (right-to-left) for checking layouts are ready to translate.
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                when {
                    keystoreFile != null -> signingConfigs.getByName("release")

                    signWithDebugKey -> signingConfigs.getByName("debug")

                    // Unsigned: can't be installed, which is the point without the real key.
                    else -> null
                }
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
    implementation(project(":feature:widget"))
    implementation(project(":feature:healthconnect"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.process)
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
    testImplementation(project(":core:testing"))
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Accessibility checks on every screen; they need a real device.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
}

/** 1.2.3 -> 10203. Each part must stay below 100, which leaves room for years of releases. */
fun versionCodeOf(version: String): Int {
    val parts =
        Regex("""(\d+)\.(\d+)\.(\d+)""")
            .matchEntire(version)
            ?.groupValues
            ?.drop(1)
            ?.map(String::toInt)
    require(
        parts != null &&
            parts.all {
                it < 100
            },
    ) { "Version must be MAJOR.MINOR.PATCH with each part below 100, was $version" }
    return parts[0] * 10_000 + parts[1] * 100 + parts[2]
}
