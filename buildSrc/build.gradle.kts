plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven("https://jitpack.io")
}

dependencies {
    api(libs.android.gradle.plugin)
    api(libs.kotlin.gradle.plugin)
    api(libs.ktfmt.gradle.plugin)
}

ktfmt { kotlinLangStyle() }

gradlePlugin {
    plugins {
        register("androidAppConvention") {
            id = "app.convention"
            implementationClass = "AndroidAppConventionPlugin"
        }
        register("androidLibConvention") {
            id = "lib.convention"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("copyApkPlugin") {
            id = "copy-apk-plugin"
            implementationClass = "CopyApkPlugin"
        }
    }
}
