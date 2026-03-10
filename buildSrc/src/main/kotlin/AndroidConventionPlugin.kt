import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            with(pluginManager) {
                val composePluginId = libs.findPlugin("compose-compiler").get().get().pluginId
                apply(composePluginId)
            }

            val extension = extensions.getByType(CommonExtension::class.java)
            configureAndroidCommon(extension)
        }
    }

    private fun Project.configureAndroidCommon(extension: CommonExtension) {
        extension.apply {
            compileSdk = ProjectConfig.compileSdk

            defaultConfig.apply {
                minSdk = ProjectConfig.minSdk
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                vectorDrawables.useSupportLibrary = true
            }

            compileOptions.apply {
                sourceCompatibility = ProjectConfig.javaVersion
                targetCompatibility = ProjectConfig.javaVersion
                isCoreLibraryDesugaringEnabled = true
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions { jvmTarget.set(ProjectConfig.jvmTarget) }
            }

            buildFeatures.apply { compose = true }
        }
    }
}
