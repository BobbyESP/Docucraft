import ProjectConfig.setupMainConfiguration
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions.with

class AndroidLibraryConventionPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            applyPlugins(
                "android-library",
                "kotlin-serialization"
            )

            val extension = extensions.getByType(CommonExtension::class.java)
            setupMainConfiguration(extension)
        }
    }
}