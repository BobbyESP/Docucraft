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
        val apkDir = apkFolder.get().asFile
        if (!apkDir.exists()) {
            logger.info("❌ APK directory does not exist, skipping copy.")
            return
        }

        val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
        if (builtArtifacts == null || builtArtifacts.elements.isEmpty()) {
            logger.info("❌ No APK artifacts found to copy.")
            return
        }

        val outputDir = outputDirectory.get().asFile
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        builtArtifacts.elements.forEach { artifact ->
            val customName =
                "${appName.get()}-${versionNameStr.get()}-${builtArtifacts.variantName}.apk"
            val sourceFile = File(artifact.outputFile)
            val targetFile = outputDirectory.get().file(customName).asFile

            sourceFile.copyTo(targetFile, overwrite = true)
            logger.lifecycle("✅ [SUCCESSFUL] - Copied APK to: ${targetFile.absolutePath}")
        }
    }
}
