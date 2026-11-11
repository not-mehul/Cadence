import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Enables Jetpack Compose on an Android application or library module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-compose"))
            extensions.findByType<ApplicationExtension>()?.buildFeatures?.compose = true
            extensions.findByType<LibraryExtension>()?.buildFeatures?.compose = true
            // Material 3 Expressive (shape morphing, expressive motion, toggle buttons) is still marked
            // experimental; every Compose module has Material 3 on its classpath.
            tasks.withType<KotlinCompilationTask<*>>().configureEach {
                compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            }

            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
