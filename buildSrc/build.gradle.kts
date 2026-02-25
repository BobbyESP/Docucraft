plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktfmt.gradle)
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.gradle)
}

allprojects {
    ktfmt {
        // Google style - 2 space indentation & automatically adds/removes trailing commas
        // googleStyle()

        // KotlinLang style - 4 space indentation - From
        // https://kotlinlang.org/docs/coding-conventions.html
        kotlinLangStyle()
    }
}

gradlePlugin {
    plugins {
        register("copyApkPlugin") {
            id = "copy-apk-plugin"
            implementationClass = "CopyApkPlugin"
        }
    }
}
