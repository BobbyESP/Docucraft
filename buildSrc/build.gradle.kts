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
            id = "android.app.convention"
            implementationClass = "AndroidAppConventionPlugin"
        }

        register("androidFeatureConvention") {
            id = "android.feature.convention"
            implementationClass = "FeatureModuleConventionPlugin"
        }

        register("androidCoreConvention") {
            id = "android.core.convention"
            implementationClass = "CoreModuleConventionPlugin"
        }

        register("androidCoreUiConvention") {
            id = "android.core.ui.convention"
            implementationClass = "CoreUiConventionPlugin"
        }

        register("copyApkPlugin") {
            id = "copy-apk-plugin"
            implementationClass = "CopyApkPlugin"
        }
    }
}
