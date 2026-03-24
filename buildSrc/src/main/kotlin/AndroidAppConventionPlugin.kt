import ProjectConfig.setupMainConfiguration
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions

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

            val extension = extensions.getByType(CommonExtension::class.java)
            setupMainConfiguration(extension)
            applyAndroidAppConfiguration(extension)
        }
    }

    private fun applyAndroidAppConfiguration(extension: CommonExtension) {
        extension.apply {
            defaultConfig.apply {
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                vectorDrawables.useSupportLibrary = true
            }

            buildFeatures.apply { compose = true }
        }
    }
}