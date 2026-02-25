import com.android.build.api.variant.BuiltArtifactsLoader
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class CopyApkTask : DefaultTask() {

    @get:InputFiles abstract val apkFolder: DirectoryProperty

    @get:Internal abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @get:Input abstract val appName: Property<String>

    @get:Input abstract val versionNameStr: Property<String>

    @TaskAction
    fun taskAction() {
        val outputDir = outputDirectory.get().asFile
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val builtArtifacts =
            builtArtifactsLoader.get().load(apkFolder.get())
                ?: throw RuntimeException("Cannot load APKs")

        builtArtifacts.elements.forEach { artifact ->
            // Create custom name: Docucraft-1.0.0-beta.9-debug.apk
            val customName =
                "${appName.get()}-${versionNameStr.get()}-${builtArtifacts.variantName}.apk"

            val sourceFile = File(artifact.outputFile)
            val targetFile = outputDirectory.get().file(customName).asFile

            sourceFile.copyTo(targetFile, overwrite = true)

            logger.lifecycle("✅ Copied APK: ${targetFile.name}")
            logger.lifecycle("\tLocation: ${targetFile.absolutePath}")
        }
    }
}
