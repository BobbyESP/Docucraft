import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

object ProjectConfig {
    const val minSdk = 24
    const val compileSdk = 37

    /** Android 17's first minor release, 37.1, which Compose 1.13 requires to compile against. */
    const val compileSdkMinor = 1
    const val targetSdk = 37

    val javaVersion = JavaVersion.VERSION_17
    val jvmTarget = JvmTarget.JVM_17
}
