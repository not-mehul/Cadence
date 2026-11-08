import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A UI feature module: Android library + Compose + Hilt view models + the core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("cadence.android.library")
            pluginManager.apply("cadence.android.compose")
            pluginManager.apply("cadence.hilt")

            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:data"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-hilt-viewmodel-compose"))

                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("kotlin-test"))
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
