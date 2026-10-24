// SanibonaniSave — root build file
// Android Studio Ladybug (2026.1.2) / AGP 9.3.1 / Gradle 9.0
plugins {
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.kotlin.android)        apply false
    alias(libs.plugins.kotlin.compose)        apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.kotlin.parcelize)      apply false
    alias(libs.plugins.hilt)                  apply false
    alias(libs.plugins.ksp)                   apply false
    alias(libs.plugins.google.services)       apply false
    alias(libs.plugins.firebase.crashlytics)  apply false
    alias(libs.plugins.room)                  apply false
}
