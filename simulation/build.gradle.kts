plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sanibonani.save.simulation"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }

}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)
    // mockk is a genuine *runtime* dependency, not just test: Main.kt's main() entrypoint
    // mocks android.util.Log / android.os.Looper so the simulation can run on the JVM
    // outside an emulator. `implementation` is correct here; it only reaches :app's test
    // classpaths (which is where :app consumes :simulation), not :app's main classpath.
    implementation(libs.mockk)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.ktor.client.android)
    androidTestImplementation(libs.mockk.android)
}

// To keep the 'run' task working, we need to carefully set the classpath
tasks.register<JavaExec>("run") {
    mainClass.set("com.sanibonani.save.simulation.MainKt")
    // This is a bit hacky for an Android library but can work for JVM-only parts
    classpath = sourceSets["main"].runtimeClasspath
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}
