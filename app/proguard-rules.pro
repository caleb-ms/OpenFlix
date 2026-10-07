# ============================================================================
# OpenFlix ProGuard / R8 Configuration Rules
# ============================================================================

# ----------------------------------------------------------------------------
# General Keep Attributes & Optimizations
# ----------------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# Keep line numbers for readable crash reports / stacktraces
-renamesourcefileattribute SourceFile

# ----------------------------------------------------------------------------
# LibVLC Keep Rules (CRITICAL for Native JNI & Player)
# LibVLC native C/C++ code (libvlc.so) makes callbacks into Java classes via
# direct JNI reflection signatures. Obfuscating or removing these classes
# or methods will cause instant JNI crashes during video playback.
# ----------------------------------------------------------------------------
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-keep interface org.videolan.medialibrary.** { *; }

# Keep all native JNI methods across the application
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ----------------------------------------------------------------------------
# Data Models & Serialization Keep Rules
# ----------------------------------------------------------------------------
# Gson / Retrofit Remote API Models (Fields are accessed reflectively by Gson)
-keep class com.calebms.openflix.data.remote.** { *; }

# Kotlinx.Serialization / Ktor Server Remote Messages (PC Casting / Remote Control)
-keep class com.calebms.openflix.data.server.** { *; }
-keepclassmembers class com.calebms.openflix.data.server.** {
    *** Companion;
    *** serializer(...);
}

# Room Database Entities
-keep class com.calebms.openflix.data.local.entities.** { *; }

# ----------------------------------------------------------------------------
# Ktor Embedded Server, CIO Engine & SLF4J Logging
# Used for Local Media Server & Remote Player Streaming
# ----------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# ----------------------------------------------------------------------------
# Retrofit, Gson, & OkHttp Rules
# ----------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# ----------------------------------------------------------------------------
# Media3 & AndroidX Media Rules
# ----------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.** { *; }

# ----------------------------------------------------------------------------
# Coil Image Loading Rules
# ----------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**
