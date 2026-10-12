import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.sanibonani.save.build.getSafeProp

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

android {
    namespace   = "com.sanibonani.save"
    compileSdk  = 37

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(
                "$projectDir/schemas",
                "$projectDir/../data/schemas"
            )
        }
    }

    defaultConfig {
        applicationId   = "com.sanibonani.save"
        minSdk          = 26          // Android 8.0 — covers ~98% SA active devices
        targetSdk       = 37
        versionCode     = 1
        versionName     = "1.0.0"
        testInstrumentationRunner = "com.sanibonani.save.HiltTestRunner"

        // ── Supabase credentials (read from local.properties) ──────────────
        buildConfigField("String", "SUPABASE_URL_LOCAL", getSafeProp(project, "SUPABASE_URL_LOCAL", "http://127.0.0.1:45421"))
        buildConfigField("String", "SUPABASE_ANON_KEY_LOCAL", getSafeProp(project, "SUPABASE_ANON_KEY_LOCAL"))
        buildConfigField("String", "SUPABASE_URL_ONLINE", getSafeProp(project, "SUPABASE_URL_ONLINE", "https://your-project.supabase.co"))
        buildConfigField("String", "SUPABASE_ANON_KEY_ONLINE", getSafeProp(project, "SUPABASE_ANON_KEY_ONLINE"))

        // Legacy support (fallback to local if not specified)
        buildConfigField("String", "SUPABASE_URL", getSafeProp(project, "SUPABASE_URL", "https://your-project.supabase.co"))
        buildConfigField("String", "SUPABASE_ANON_KEY", getSafeProp(project, "SUPABASE_ANON_KEY"))

        // ── Geoapify (Address Autocomplete) ──────────────────────────────────────
        buildConfigField("String", "GEOAPIFY_API_KEY", getSafeProp(project, "GEOAPIFY_API_KEY", "placeholder_key"))

        // ── Platform Admin Policy ───────────────────────────────────────────────
        buildConfigField("String", "PLATFORM_ADMIN_EMAIL", getSafeProp(project, "PLATFORM_ADMIN_EMAIL"))
        // 🏦 Payment Gateways
        buildConfigField("String", "PAYFAST_MERCHANT_ID", getSafeProp(project, "PAYFAST_MERCHANT_ID", "\"10000100\""))
        buildConfigField("String", "PAYFAST_MERCHANT_KEY", getSafeProp(project, "PAYFAST_MERCHANT_KEY", "\"46f0cd694581a\""))
        buildConfigField("String", "PAYFAST_URL", getSafeProp(project, "PAYFAST_URL", "\"https://sandbox.payfast.co.za/eng/process\""))
        buildConfigField("String", "PAYFAST_QUERY_URL", getSafeProp(project, "PAYFAST_QUERY_URL", "\"https://sandbox.payfast.co.za/eng/query/fetch\""))
        buildConfigField("String", "STITCH_API_URL", getSafeProp(project, "STITCH_API_URL", "\"https://api.stitch.money/graphql\""))
    }

    // 🛡️ Signing configs 🛡️───────────────────────────────────────────────────────
    // Reads from local.properties for local builds; falls back to env vars for CI/CD.
    signingConfigs {
        create("release") {
            val signingProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) load(f.inputStream())
            }
            val keystorePath = signingProps.getProperty("KEYSTORE_PATH") ?: System.getenv("KEYSTORE_PATH")
            storeFile = keystorePath?.let { rootProject.file(it) }
            storePassword = signingProps.getProperty("KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = signingProps.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS") ?: "sanibonani_release"
            keyPassword = signingProps.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isDebuggable        = true
            versionNameSuffix   = "-DEBUG"
            isMinifyEnabled     = false
            resValue("string", "app_name", "SanibonaniSave [DEV]")
            buildConfigField("boolean", "RELEASE_BUILD", "false")
            buildConfigField("String", "SUPABASE_SERVICE_ROLE_KEY", getSafeProp(project, "SUPABASE_SERVICE_ROLE_KEY"))
        }
        release {
            isMinifyEnabled     = true
            isShrinkResources   = true
            signingConfig       = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "RELEASE_BUILD", "true")
            buildConfigField("String", "SUPABASE_SERVICE_ROLE_KEY", "\"\"")
            resValue("string", "app_name", "SanibonaniSave")
        }
    }

    compileOptions {
        sourceCompatibility             = JavaVersion.VERSION_17
        targetCompatibility             = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled  = false
    }

    configurations.all {
        resolutionStrategy {
            force(libs.kotlinx.coroutines.core)
            force(libs.kotlinx.coroutines.android)
            force(libs.kotlinx.coroutines.test)
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
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
                "META-INF/LICENSE-notice.md",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

// Room writes the latest migration schema during data compilation; include it before
// Android test assets are merged so MigrationTestHelper can validate the current version.
tasks.configureEach {
    if (name == "mergeDebugAndroidTestAssets") {
        dependsOn(":data:copyRoomSchemas")
    }
}

ksp {
    arg("dagger.fastInit", "enabled")
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlin.time.ExperimentalTime"
        )
    }
    if (name == "compileDebugAndroidTestKotlin") {
        exclude(
            "**/e2e/**"
        )
    }
}

tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    doNotTrackState("Android UTP lock files are transient and not stable task outputs.")
}

dependencies {
    // ── Compose BOM ────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation)
    implementation(libs.compose.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.ext)
    implementation(libs.compose.animation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.compose.ui.text.google.fonts)

    // ── Material Components (XML support for themes) ──────────────────────
    implementation(libs.material)

    // ── AndroidX core ──────────────────────────────────────────────────────
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.preference.ktx)
    implementation(libs.play.services.location)
    implementation(libs.splashscreen)
    implementation(libs.biometric)
    implementation(libs.security.crypto)

    // ── WorkManager ────────────────────────────────────────────────────────
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // ── Room ───────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    // Removed redundant room-compiler in app module (database is in :data)

    // ── Hilt DI ────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Supabase BOM + modules ──────────────────────────────────────────────
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)

    // ── Ktor (HTTP client for Supabase) ─────────────────────────────────────
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)
    androidTestImplementation(libs.ktor.client.mock)

    // ── Kotlin coroutines & serialization ───────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // ── Networking (YoCo REST integration) ──────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ── Image loading ────────────────────────────────────────────────────────
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // ── Maps — OpenStreetMap (no API key required) ───────────────────────────
    implementation(libs.osmdroid)
    implementation(libs.osm.bonus.pack)

    // ── Permissions ──────────────────────────────────────────────────────────
    implementation(libs.accompanist.permissions)

    // ── CameraX ──────────────────────────────────────────────────────────────
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.extensions)

    // ── Modules ──────────────────────────────────────────────────────────────
    implementation(project(":domain"))
    implementation(project(":data"))

    // ── Firebase BOM + modules ───────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation(project(":simulation"))
    androidTestImplementation(project(":simulation"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    kspTest(libs.hilt.android.compiler)

    // ── Android instrumented tests ────────────────────────────────────────────
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    // HiltTestRunner extends AndroidJUnitRunner, which lives in androidx.test:runner.
    // This used to arrive transitively via espresso-core (removed as unused), so it is
    // now declared explicitly.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.room.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    debugImplementation(libs.leakcanary.android)
}
