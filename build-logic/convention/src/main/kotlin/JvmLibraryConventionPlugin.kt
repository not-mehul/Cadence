import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * A pure Kotlin/JVM module. It cannot depend on the Android SDK, which is how we
 * guarantee that `:core:domain` stays free of platform code.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = CADENCE_JAVA_VERSION
                targetCompatibility = CADENCE_JAVA_VERSION
            }
            configureKotlinCompiler()
            configureKtlint()

            dependencies {
                add("testImplementation", platform(libs.lib("junit-bom")))
                add("testImplementation", libs.lib("junit-jupiter"))
                add("testImplementation", libs.lib("kotlin-test"))
                add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
            }
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}
