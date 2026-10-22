import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature: its screens, ViewModels, its own domain and data if any, and its DI. It brings what
 * every feature builds on, so a feature's build file names only what is its own.
 *
 * It also fails the build when a feature depends on a module it must not see. The rules were
 * written down long before they were checked, and a single `implementation(project(...))` was
 * enough to break one without anything noticing: a feature that sees another feature, the
 * catalogue's storage or an engine's implementation can no longer be built, tested or replaced on
 * its own.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("docucraft.android.library")
            pluginManager.apply("docucraft.android.compose")

            dependencies {
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:navigation"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":library:domain"))
                add("implementation", project(":library:ui"))

                add("implementation", libs.bundle("androidx-lifecycle"))
                add("implementation", libs.bundle("compose"))
                add("implementation", libs.bundle("coroutines"))
                add("implementation", libs.bundle("haze"))
                add("implementation", libs.bundle("koin"))
                add("implementation", libs.bundle("navigation3"))
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))

                add("testImplementation", project(":library:testing"))
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }

            afterEvaluate { checkDependencyRules() }
        }
    }

    /**
     * What a feature may see is listed, not what it may not: a new engine implementation, or a new
     * feature, is refused without anyone having to remember to add it here.
     */
    private fun Project.checkDependencyRules() {
        val forbidden =
            configurations
                .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                .map { it.path }
                // AGP makes a module's test configurations depend on the module itself.
                .filter { it != path }
                .filterNot { it.isAllowedInFeature() }
                .distinct()
                .sorted()
        if (forbidden.isNotEmpty()) {
            throw GradleException(
                "$path depends on $forbidden. A feature sees :core:* and :library:* (never their " +
                    ":data), the engines' contracts (*:api) and :composepdf. It opens another " +
                    "feature by a key of :core:navigation, and implementations are bound in :app."
            )
        }
    }

    private fun String.isAllowedInFeature(): Boolean =
        when {
            this == ":core:data" || this == ":library:data" -> false
            startsWith(":core:") || startsWith(":library:") -> true
            endsWith(":api") -> true
            // The PDF engine is generic, and the viewer draws with it through its public hooks.
            this == ":composepdf" -> true
            else -> false
        }
}
