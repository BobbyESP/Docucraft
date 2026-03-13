plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ktfmt.gradle.plugin)
}

ktfmt { kotlinLangStyle() }

gradlePlugin {
    plugins {
        register("androidConvention") {
            id = "docucraft.android.convention"
            implementationClass = "AndroidConventionPlugin"
        }
        register("copyApkPlugin") {
            id = "copy-apk-plugin"
            implementationClass = "CopyApkPlugin"
        }
    }
}
