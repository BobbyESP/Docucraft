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


# Navigation 3 keys.
#
# `rememberNavBackStack` saves the back stack through `NavKeySerializer`, which writes each key's
# fully qualified class name and reads it back with `Class.forName(name).kotlin.serializer()`.
# That is reflection, so R8 cannot see it: a key whose class is renamed or removed comes back as a
# crash after process death, and only in release builds.
#
# These rules are written against the `NavKey` interface rather than against a package, so a key
# stays covered wherever its feature decides to keep it.
-keep class androidx.navigation3.runtime.NavKey
-keep class * implements androidx.navigation3.runtime.NavKey { *; }
-keepclassmembers class * implements androidx.navigation3.runtime.NavKey {
    public static ** Companion;
    public static ** INSTANCE;
    public static kotlinx.serialization.KSerializer serializer(...);
}

# Nothing is needed for the generated serializers themselves: kotlinx-serialization ships consumer
# rules that keep `Companion`, `serializer()` and `INSTANCE` on every @Serializable class. What it
# deliberately does *not* do is pin the class name — which is the one thing the reflective lookup
# above depends on, hence the rules in this block.

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
# RevenueCat still references Play Billing's QueryPurchaseHistory* API, removed in Billing 9.
# The call sites are unreachable for us (we never query purchase history), so the references are
# dead weight R8 would otherwise refuse to shrink around. Pre-existing; unrelated to navigation.
-dontwarn com.android.billingclient.api.QueryPurchaseHistoryParams$Builder
-dontwarn com.android.billingclient.api.QueryPurchaseHistoryParams
