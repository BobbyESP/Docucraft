import ProjectConfig.setupJvmConfiguration
import ProjectConfig.setupMainConfiguration
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.configure

class CoreUiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            applyPlugins(
                "android-library",
                "kotlin-compose"
            )

            extensions.configure<CommonExtension> {
                setupMainConfiguration(this)

                buildFeatures.compose = true
            }

            setupJvmConfiguration()
        }
    }
}