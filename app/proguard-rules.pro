# Keep important Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.lifecycle.ViewModel

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Preserve Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bobbyesp.docucraft.**$$serializer { *; }
-keepclassmembers class com.bobbyesp.docucraft.** {
    *** Companion;
}
-keepclasseswithmembers class com.bobbyesp.docucraft.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose-related rules
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }
-keepclassmembers class * extends androidx.compose.runtime.State {
    <methods>;
}

# Koin DI rules
-keep class org.koin.** { *; }
-keep class * implements org.koin.core.KoinComponent { *; }

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# MLKit document scanner
-keep class com.google.mlkit.vision.documentscanner.** { *; }

# FileKit
-keep class io.github.vinceglb.filekit.** { *; }

# Firebase and Google Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Sonner toast library
-keep class io.github.dokar3.sonner.** { *; }

# QR code library
-keep class io.github.g0dkar.qrcode.** { *; }

# General optimization
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# For debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep ScannedPdf model for PDF deletion dialog
-keep class com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedPdf { *; }

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep R8 rules from crashing with custom view attributes
-keep class * extends androidx.databinding.DataBinderMapper { *; }

# Keep app Routes for navigation
-keep class com.bobbyesp.docucraft.core.presentation.common.Route { *; }
-keepclassmembers class com.bobbyesp.docucraft.core.presentation.common.Route$** { *; }

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean