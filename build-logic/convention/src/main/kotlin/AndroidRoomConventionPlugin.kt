import com.android.build.api.dsl.CommonExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * Room, for the module that holds a database.
 *
 * Its schemas are exported to the module's `schemas/` folder, which is committed, and handed to the
 * instrumented tests as assets: the migration tests build each old version of the database from
 * them.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            val schemas = File(projectDir, "schemas")
            extensions.configure<KspExtension> { arg(RoomSchemaArgProvider(schemas)) }

            extensions
                .getByType(CommonExtension::class.java)
                .sourceSets
                .getByName("androidTest")
                .assets
                .directories += "schemas"

            dependencies {
                add("implementation", libs.library("room-runtime"))
                add("implementation", libs.library("room-ktx"))
                add("ksp", libs.library("room-compiler"))
                add("androidTestImplementation", libs.library("room-testing"))
            }
        }
    }
}

private class RoomSchemaArgProvider(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) val schemaDir: File
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> {
        if (!schemaDir.exists()) schemaDir.mkdirs()
        return listOf("room.schemaLocation=${schemaDir.path}")
    }
}
