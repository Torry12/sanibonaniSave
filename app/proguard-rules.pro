# ─────────────────────────────────────────────────────────────────────────────
# SanibonaniSave ProGuard / R8 Rules
# ─────────────────────────────────────────────────────────────────────────────

# ── Kotlin Serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { CREATOR <fields>; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Supabase / Ktor ────────────────────────────────────────────────────────────
-keep class io.github.jan.tennert.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── Retrofit / OkHttp ─────────────────────────────────────────────────────────
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.jakewharton.retrofit2.** { *; }

# ── Hilt ───────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembers class * { @dagger.hilt.* <fields>; }

# ── Room ────────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# ── OSMDroid ────────────────────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ── WebView JS bridge (Leaflet map) ────────────────────────────────────────────
# Ensure @JavascriptInterface methods are not stripped/renamed in release builds.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── MPAndroidChart ──────────────────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ── Firebase ────────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Coil ────────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── App data models (never obfuscate — Supabase uses field names for mapping) ──
# Keep domain models (formerly data.model)
-keep class com.sanibonani.save.domain.model.** { *; }
-keepclassmembers class com.sanibonani.save.domain.model.** { *; }

# Keep remote data models
-keep class com.sanibonani.save.data.remote.model.** { *; }
-keepclassmembers class com.sanibonani.save.data.remote.model.** { *; }

# ── kotlinx-datetime ────────────────────────────────────────────────────────────
-keep class kotlinx.datetime.** { *; }

# ── WorkManager ─────────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# ── Enum toString (needed for Supabase enum serialization) ──────────────────────
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
