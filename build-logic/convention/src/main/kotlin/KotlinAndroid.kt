import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.bundle(alias: String) = findBundle(alias).get()

/**
 * What every Android module shares, application or library: the SDKs, the Java level and
 * desugaring.
 *
 * Desugaring is on everywhere because `:composepdf` needs it, and AGP asks it of every module that
 * depends on a library that has it on.
 */
@Suppress("UnstableApiUsage")
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = ProjectConfig.compileSdk
        compileSdkMinor = ProjectConfig.compileSdkMinor

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
    }

    configureKotlinJvmTarget()

    dependencies { add("coreLibraryDesugaring", libs.library("desugar-jdk-libs")) }
}

internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions { jvmTarget.set(ProjectConfig.jvmTarget) }
    }
}
