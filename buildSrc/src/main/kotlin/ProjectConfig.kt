import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

object ProjectConfig {
    const val minSdk = 24
    const val compileSdk = 36
    const val targetSdk = 36

    val javaVersion = JavaVersion.VERSION_17
    val jvmTarget = JvmTarget.JVM_17

    fun Project.setupMainConfiguration(extension: CommonExtension) {
        extension.apply {
            this.compileSdk = ProjectConfig.compileSdk

            defaultConfig.apply {
                minSdk = ProjectConfig.minSdk

            }

            setupKotlinConfiguration(extension)
        }
    }

    fun Project.setupJvmConfiguration() {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(ProjectConfig.jvmTarget)
            }
        }
    }

    fun Project.setupKotlinConfiguration(extension: CommonExtension) {
        extension.apply {
            compileOptions.apply {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions { jvmTarget.set(ProjectConfig.jvmTarget) }
            }
        }
    }
}
