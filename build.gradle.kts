// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    //alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.gms) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.stability.analyzer) apply false
    //alias(libs.plugins.androidTest) apply false
}

sealed class Version(
    open val major: Int,
    open val minor: Int,
    open val patch: Int,
    open val build: Int = 0
) {
    protected abstract val stageCode: Int // 0: Alpha, 1: Beta, 2: RC, 3: Stable
    protected abstract val stageName: String

    fun toVersionName(): String {
        return if (this is Stable) {
            "$major.$minor.$patch"
        } else {
            "$major.$minor.$patch-$stageName.$build"
        }
    }

    /**
     * Format: MMMMmmPPsBB (Major - Minor - Patch - stage - Build)
     * Major: Max. 200 - Example
     * Minor: Max. 99
     * Patch: Max. 99
     * Stage: 0-3
     * Build: 0-99
     */
    fun toVersionCode(): Int {
        return (major * 10_000_000) + (minor * 100_000) + (patch * 1_000) + (stageCode * 100) + build
    }

    data class Alpha(
        override val major: Int,
        override val minor: Int,
        override val patch: Int,
        override val build: Int
    ) : Version(major, minor, patch, build) {
        override val stageCode = 0
        override val stageName = "alpha"
    }

    data class Beta(
        override val major: Int,
        override val minor: Int,
        override val patch: Int,
        override val build: Int
    ) : Version(major, minor, patch, build) {
        override val stageCode = 1
        override val stageName = "beta"
    }

    data class ReleaseCandidate(
        override val major: Int,
        override val minor: Int,
        override val patch: Int,
        override val build: Int
    ) : Version(major, minor, patch, build) {
        override val stageCode = 2
        override val stageName = "rc"
    }

    data class Stable(
        override val major: Int,
        override val minor: Int,
        override val patch: Int
    ) : Version(major, minor, patch) {
        override val stageCode = 3
        override val stageName = ""
        override val build = 0
    }
}

val currentVersion: Version = Version.Beta(
    major = 1,
    minor = 0,
    patch = 0,
    build = 8
)

val versionCode by extra(currentVersion.toVersionCode())
val versionName by extra(currentVersion.toVersionName())