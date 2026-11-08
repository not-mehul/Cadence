import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jlleitschuh.gradle.ktlint.KtlintExtension

internal fun Project.configureKtlint() {
    pluginManager.apply(libs.pluginId("ktlint"))
    extensions.configure<KtlintExtension> {
        version.set(libs.version("ktlint"))
        android.set(true)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }
    dependencies {
        add("ktlintRuleset", libs.lib("composeRules-ktlint"))
    }
}
