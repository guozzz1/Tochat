# === Retrofit ===
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# === OkHttp ===
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# === Kotlinx Serialization ===
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.gzzz.toimage.data.remote.dto.** { *; }
-keepclasseswithmembers class com.gzzz.toimage.domain.model.** { *; }

# Keep @Serializable classes and their companion objects
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# === Hilt ===
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# === Room ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# === Coil ===
-dontwarn coil.**

# === Compose ===
-dontwarn androidx.compose.**

# === Security Crypto ===
-keep class androidx.security.crypto.** { *; }

# === Keep app components ===
-keep class com.gzzz.toimage.ToimageApp { *; }
-keep class com.gzzz.toimage.MainActivity { *; }
-keep class com.gzzz.toimage.service.GenerationService { *; }

# === Remove logging in release ===
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
