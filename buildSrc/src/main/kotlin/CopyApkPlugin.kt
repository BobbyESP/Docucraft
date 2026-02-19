import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class CopyApkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            // variantName será "Debug", "Release", etc.
            val variantName = variant.name.replaceFirstChar { it.uppercase() }

            val copyTask = project.tasks.register<CopyApkTask>("copy${variantName}Apk") {
                group = "build"
                description = "Copies the ${variant.name} APK with a custom name"

                apkFolder.set((variant as ApplicationVariant).artifacts.get(SingleArtifact.APK))
                builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                outputDirectory.set(project.layout.buildDirectory.dir("outputs/apk-renamed/${variant.name}"))
                appName.set("Docucraft")
                versionNameStr.set(variant.outputs.first().versionName.getOrElse(""))
            }

            project.afterEvaluate {
                project.tasks.named("assemble${variantName}").configure {
                    finalizedBy(copyTask)
                }
            }
        }
    }
}