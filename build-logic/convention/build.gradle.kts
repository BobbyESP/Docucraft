plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt)
}

// compileOnly: the root build puts these plugins on the classpath (`apply false`), so the
// conventions apply the very same versions every module would.
dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

ktfmt { kotlinLangStyle() }

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "docucraft.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "docucraft.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "docucraft.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "docucraft.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "docucraft.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "docucraft.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("copyApk") {
            id = "docucraft.copy-apk"
            implementationClass = "CopyApkPlugin"
        }
    }
}
