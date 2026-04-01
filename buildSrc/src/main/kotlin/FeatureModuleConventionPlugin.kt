import ProjectConfig.setupJvmConfiguration
import ProjectConfig.setupMainConfiguration
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.configure

class FeatureModuleConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            applyPlugins(
                "android-library",
                "kotlin-compose",
                "kotlin-serialization",
                "kotlin-parcelize"
            )

            extensions.configure<CommonExtension> {
                setupMainConfiguration(this)

                defaultConfig.proguardFiles += project.file("proguard-rules.pro")

                buildFeatures.compose = true
            }

            setupJvmConfiguration()
        }
    }
}