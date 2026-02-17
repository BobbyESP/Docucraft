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


# Keep MLKit document scanner
-keep class com.google.mlkit.vision.documentscanner.** { *; }

# Keep FileKit
-keep class io.github.vinceglb.filekit.** { *; }

# Keep Sonner toast library
-keep class io.github.dokar3.sonner.** { *; }

# Keep QR code library
-keep class io.github.g0dkar.qrcode.** { *; }

# Keep ScannedPdf model for PDF deletion dialog
-keep class com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument { *; }

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}


# Keep app Routes for navigation
-keep class com.bobbyesp.docucraft.core.presentation.common.Route { *; }
-keepclassmembers class com.bobbyesp.docucraft.core.presentation.common.Route$** { *; }

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean