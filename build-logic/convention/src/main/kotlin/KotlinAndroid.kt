import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

internal val CADENCE_JAVA_VERSION = JavaVersion.VERSION_17

/** Settings shared by the application module and every Android library module. */
internal fun Project.configureKotlinCompiler() {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
            if (this is KotlinJvmCompilerOptions) {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}

internal fun Project.configureAndroidApplication(extension: ApplicationExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig {
            minSdk = libs.version("minSdk").toInt()
            targetSdk = libs.version("targetSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = CADENCE_JAVA_VERSION
            targetCompatibility = CADENCE_JAVA_VERSION
        }
        testOptions.unitTests.isIncludeAndroidResources = true
        configureLint(lint)
    }
    configureKotlinCompiler()
    configureAndroidTests()
}

internal fun Project.configureAndroidLibrary(extension: LibraryExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig {
            minSdk = libs.version("minSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            consumerProguardFiles("consumer-rules.pro")
        }
        compileOptions {
            sourceCompatibility = CADENCE_JAVA_VERSION
            targetCompatibility = CADENCE_JAVA_VERSION
        }
        testOptions {
            targetSdk = libs.version("targetSdk").toInt()
            unitTests.isIncludeAndroidResources = true
        }
        configureLint(lint)
    }
    configureKotlinCompiler()
    configureAndroidTests()
}

private fun Project.configureLint(lint: com.android.build.api.dsl.Lint) {
    lint.apply {
        targetSdk = libs.version("targetSdk").toInt()
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        // Version-freshness checks change daily as new releases land; dependency
        // upgrades are a deliberate, separate task rather than a build failure.
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
    }
}

private fun Project.configureAndroidTests() {
    // Optional: where Robolectric downloads its Android runtime jars from (default: Maven Central).
    // Set `cadence.robolectric.repoUrl` in ~/.gradle/gradle.properties to use a mirror.
    val robolectricRepoUrl = providers.gradleProperty("cadence.robolectric.repoUrl").orNull
    tasks.withType<Test>().configureEach {
        robolectricRepoUrl?.let { systemProperty("robolectric.dependency.repo.url", it) }
        // Robolectric needs headroom; keep test logs readable in CI.
        maxHeapSize = "2g"
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
