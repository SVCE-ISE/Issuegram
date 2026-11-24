# Issuegram ProGuard / R8 Rules

# KotlinX Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.svce.issuegram.data.model.** { *; }
-keep,includedescriptorclasses class com.svce.issuegram.data.local.ReportDraftStore** { *; }
-keep,includedescriptorclasses class com.svce.issuegram.ui.navigation.** { *; }

# Firebase & Firestore Models
-keepattributes Signature
-keepclassmembers class com.svce.issuegram.data.model.** {
    <init>(...);
    <fields>;
    <methods>;
}

# Android Jetpack Compose Navigation & ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Google Maps & Play Services
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }

# OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
