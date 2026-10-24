import com.sanibonani.save.build.getSafeProp

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.sanibonani.save.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Supabase credentials (read from local.properties) ──────────────
        buildConfigField("String", "SUPABASE_URL_LOCAL", getSafeProp(project, "SUPABASE_URL_LOCAL", "\"http://127.0.0.1:45421\""))
        buildConfigField("String", "SUPABASE_ANON_KEY_LOCAL", getSafeProp(project, "SUPABASE_ANON_KEY_LOCAL"))
        buildConfigField("String", "SUPABASE_URL_ONLINE", getSafeProp(project, "SUPABASE_URL_ONLINE", "\"https://your-project.supabase.co\""))
        buildConfigField("String", "SUPABASE_ANON_KEY_ONLINE", getSafeProp(project, "SUPABASE_ANON_KEY_ONLINE"))

        // Payment Gateways
        buildConfigField("String", "PAYFAST_MERCHANT_ID", getSafeProp(project, "PAYFAST_MERCHANT_ID", "\"10000100\""))
        buildConfigField("String", "PAYFAST_MERCHANT_KEY", getSafeProp(project, "PAYFAST_MERCHANT_KEY", "\"46f0cd694581a\""))
        buildConfigField("String", "PAYFAST_URL", getSafeProp(project, "PAYFAST_URL", "\"https://sandbox.payfast.co.za/eng/process\""))
        buildConfigField("String", "PAYFAST_QUERY_URL", getSafeProp(project, "PAYFAST_QUERY_URL", "\"https://sandbox.payfast.co.za/eng/query/fetch\""))
        buildConfigField("String", "STITCH_API_URL", getSafeProp(project, "STITCH_API_URL", "\"https://api.stitch.money/graphql\""))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

ksp {
    arg("dagger.fastInit", "enabled")
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Security
    implementation(libs.security.crypto)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)

    // Ktor
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.logging)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.room.testing)
    testImplementation(libs.ktor.client.mock)
}
