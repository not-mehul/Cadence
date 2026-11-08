import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A UI feature module: Android library + Compose + the shared core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("cadence.android.library")
            pluginManager.apply("cadence.android.compose")

            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:domain"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))

                add("testImplementation", libs.lib("junit4"))
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
                add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
