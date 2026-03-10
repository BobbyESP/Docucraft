import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object ProjectConfig {
    const val minSdk = 24
    const val compileSdk = 36
    const val targetSdk = 36

    val javaVersion = JavaVersion.VERSION_17
    val jvmTarget = JvmTarget.JVM_17
}
