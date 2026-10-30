# ---- PRISM JNI bridge ----
-keep class com.prism.ot.ml.** { *; }
-keepclasseswithmembernames class com.prism.ot.ml.** {
    native <methods>;
}

# Annotation used to mark classes/members referenced from Rust via JNI
-keep @com.prism.ot.ml.KeepForJni class * { *; }
-keep @com.prism.ot.ml.KeepForJni interface * { *; }

# Rust loads these reflective call sites by name
-keepclassmembers class com.prism.ot.codec.** {
    <init>(...);
    *;
}

# ---- Kotlin serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.prism.ot.**$$serializer { *; }
-keepclassmembers class com.prism.ot.** {
    *** Companion;
}
-keepclasseswithmembers class com.prism.ot.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Hilt / Dagger ----
-keep,allowobfuscation,allowshrinking interface dagger.hilt.internal.GeneratedComponent
-keep class dagger.hilt.** { *; }

# ---- CameraX ----
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---- Okio ----
-dontwarn okio.**

# ---- Enums used by serialization in transfer sessions ----
-keepclassmembers enum com.prism.ot.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
