import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// ── Load secrets from local.properties (never committed to git) ───────────────
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

/**
 * Safely extracts a property from local.properties and ensures it's quoted correctly
 * for BuildConfig, even if the user manually added quotes in the properties file.
 */
fun getSafeProp(key: String, default: String = ""): String {
    val raw = localProps.getProperty(key, default)
    // Strip existing quotes if present, then wrap in exactly one set of escaped quotes
    val clean = raw.trim().removeSurrounding("\"")
    return "\"$clean\""
}

android {
    namespace   = "com.sanibonani.save"
    compileSdk  = 35

    defaultConfig {
        applicationId   = "com.sanibonani.save"
        minSdk          = 26          // Android 8.0 — covers ~98% SA active devices
        targetSdk       = 35
        versionCode     = 1
        versionName     = "1.0.0"
        testInstrumentationRunner = "com.sanibonani.save.HiltTestRunner"

        // ── Supabase credentials (read from local.properties) ──────────────
        buildConfigField("String", "SUPABASE_URL", getSafeProp("SUPABASE_URL", "https://your-project.supabase.co"))
        buildConfigField("String", "SUPABASE_ANON_KEY", getSafeProp("SUPABASE_ANON_KEY", "your-anon-key-here"))
        buildConfigField("String", "SUPABASE_SERVICE_ROLE_KEY", getSafeProp("SUPABASE_SERVICE_ROLE_KEY"))

        // ── YoCo payment gateway ───────────────────────────────────────────
        buildConfigField("String", "YOCO_PUBLIC_KEY", getSafeProp("YOCO_PUBLIC_KEY", "pk_test_placeholder"))
        buildConfigField("String", "YOCO_WEBHOOK_SECRET", getSafeProp("YOCO_WEBHOOK_SECRET"))

        // ── WhatsApp Business API (Meta) ──────────────────────────────────────────
        buildConfigField("String", "WHATSAPP_TOKEN", getSafeProp("WHATSAPP_TOKEN"))
        buildConfigField("String", "WHATSAPP_PHONE_NUMBER_ID", getSafeProp("WHATSAPP_PHONE_NUMBER_ID"))

        // ── Geoapify (Address Autocomplete) ──────────────────────────────────────
        buildConfigField("String", "GEOAPIFY_API_KEY", getSafeProp("GEOAPIFY_API_KEY", "placeholder_key"))

        // ── OSMDroid user agent ────────────────────────────────────────────
        buildConfigField("String", "OSM_USER_AGENT", "\"com.sanibonani.save\"")
    }

    // ── Signing configs ───────────────────────────────────────────────────────
    signingConfigs {
        create("release") {
            storeFile      = localProps.getProperty("KEYSTORE_PATH")?.let { rootProject.file(it) }
            storePassword  = localProps.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias       = localProps.getProperty("KEY_ALIAS", "sanibonani")
            keyPassword    = localProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        debug {
            isDebuggable        = true
            // Removed applicationIdSuffix = ".debug" to match the package name in google-services.json
            versionNameSuffix   = "-DEBUG"
            isMinifyEnabled     = false
            resValue("string", "app_name", "SanibonaniSave [DEV]")
        }
        release {
            isMinifyEnabled     = true
            isShrinkResources   = true
            signingConfig       = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "SanibonaniSave")
        }
    }

    compileOptions {
        sourceCompatibility             = JavaVersion.VERSION_17
        targetCompatibility             = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled  = false
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlin.time.ExperimentalTime"
        )
    }
    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

dependencies {
    // ── Desugaring (java.time for API < 26 safety net) ─────────────────────
    // coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ── Compose BOM ────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.material.icons.ext)
    implementation(libs.compose.animation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Material Components (XML support for themes) ──────────────────────
    implementation(libs.material)

    // ── AndroidX core ──────────────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.preference.ktx)
    implementation(libs.splashscreen)
    implementation(libs.browser)
    implementation(libs.biometric)
    implementation(libs.security.crypto)

    // ── WorkManager ────────────────────────────────────────────────────────
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // ── Room ───────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Hilt DI ────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Supabase BOM + modules ──────────────────────────────────────────────
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)    // Database queries
    implementation(libs.supabase.auth)         // Authentication
    implementation(libs.supabase.storage)      // File storage (documents, photos)
    implementation(libs.supabase.realtime)     // Live subscriptions (group chat, fee status)
    implementation(libs.supabase.functions)    // Edge function invocations

    // ── Ktor (HTTP client for Supabase) ─────────────────────────────────────
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)
    androidTestImplementation(libs.ktor.client.mock)

    // ── Kotlin coroutines & serialization ───────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)      // LocalDate for actuarial calculations

    // ── Networking (YoCo REST integration) ──────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ── Image loading ────────────────────────────────────────────────────────
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ── Maps — OpenStreetMap (no API key required) ───────────────────────────
    implementation(libs.osmdroid)
    implementation(libs.osm.bonus.pack)        // Routing, markers, nominatim geocoding

    // ── Charts (Actuarial & Analytics dashboards) ───────────────────────────
    implementation(libs.mp.android.chart)

    // ── Permissions ──────────────────────────────────────────────────────────
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)
    
    // ── Modules ──────────────────────────────────────────────────────────────
    implementation(project(":domain"))
    implementation(project(":data"))
    // implementation(project(":ui"))

    // ── Firebase BOM + modules ───────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)    // Push notifications
    implementation(libs.firebase.analytics)    // Usage analytics
    implementation(libs.firebase.crashlytics)  // Crash reporting

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.kotlinx.serialization)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    kspTest(libs.hilt.android.compiler)

    // ── Android instrumented tests ────────────────────────────────────────────
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.room.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
