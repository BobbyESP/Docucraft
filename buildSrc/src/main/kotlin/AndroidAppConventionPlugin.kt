import ProjectConfig.setupMainConfiguration
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions
import org.gradle.kotlin.dsl.configure

class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        Actions.with(target) {
            applyPlugins(
                "android-application",
                "kotlin-ksp",
                "kotlin-compose",
                "kotlin-serialization",
                "kotlin-parcelize",
                "ktfmt"
            )

            extensions.configure<CommonExtension> {
                setupMainConfiguration(this)
                applyAndroidAppConfiguration()
            }
        }
    }

    private fun CommonExtension.applyAndroidAppConfiguration() {
        defaultConfig.apply {
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            vectorDrawables.useSupportLibrary = true
        }

        buildFeatures.compose = true
    }
}