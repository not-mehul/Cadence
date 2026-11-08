plugins {
    alias(libs.plugins.cadence.android.library)
    alias(libs.plugins.cadence.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "app.cadence.core.data"
    sourceSets {
        // Lets Robolectric tests read the exported Room schemas (migration tests).
        getByName("test").assets.srcDir("$projectDir/schemas")
    }
}

room {
    // Exported schemas are committed; every schema change needs a migration + test.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.core.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
}
