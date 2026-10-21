import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Compose, for an Android module that already applies `docucraft.android.application` or
 * `docucraft.android.library`: the compiler plugin, and the BOM every Compose artifact is versioned
 * by.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true

            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
            }
        }
    }
}
