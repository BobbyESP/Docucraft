plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt.gradle)
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.gradle)
    implementation(libs.kotlin.gradle.plugin)
}

allprojects {
    apply(plugin = "com.ncorti.ktfmt.gradle")
    ktfmt { kotlinLangStyle() }
}

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
