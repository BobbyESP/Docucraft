import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register

class CopyApkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val androidComponents = project.extensions.findByType<ApplicationAndroidComponentsExtension>() ?: return

        androidComponents.onVariants { variant ->
            val variantName = variant.name.replaceFirstChar { it.uppercase() }

            val copyTask = project.tasks.register<CopyApkTask>("copy${variantName}Apk") {
                group = "build"
                description = "Copies the ${variant.name} APK with a custom name"

                apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
                builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                
                outputDirectory.set(
                    project.layout.buildDirectory.dir("outputs/apk_custom/${variant.name}")
                )
                
                appName.set("Docucraft")

                versionNameStr.set(variant.outputs.first().versionName.getOrElse(""))
            }

            project.tasks.matching { it.name == "assemble${variantName}" }.configureEach {
                finalizedBy(copyTask)
            }
        }
    }
}
