import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure

/**
 * A plain Kotlin module: a contract or a domain, with no Android and no Compose. Being a JVM module
 * is what keeps it that way: an Android import does not compile.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = ProjectConfig.javaVersion
                targetCompatibility = ProjectConfig.javaVersion
            }
            configureKotlinJvmTarget()

            // The name Android modules give their unit tests, so that `testDebugUnitTest` runs the
            // unit tests of every module, plain Kotlin ones included.
            tasks.register("testDebugUnitTest") {
                group = "verification"
                description = "Runs the unit tests (the same as `test`, by the Android name)."
                dependsOn("test")
            }
        }
    }
}
