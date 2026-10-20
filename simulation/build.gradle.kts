import org.gradle.api.artifacts.type.ArtifactTypeDefinition

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sanibonani.save.simulation"
    compileSdk = 37

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

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
    testImplementation(libs.junit)
}

// The seed factory stays isolated in :simulation. The app owns the live Supabase
// runner, which uses the app's debug test runtime for Android APIs.
tasks.register<JavaExec>("run") {
    val app = project(":app")
    val android = app.extensions.getByType<com.android.build.gradle.BaseExtension>()
    val sdkPlatformPrefix = "android-${android.compileSdkVersion?.removePrefix("android-")}"
    val androidJar = android.sdkDirectory.resolve("platforms")
        .listFiles()
        ?.filter { it.isDirectory && it.name.startsWith(sdkPlatformPrefix) }
        ?.maxByOrNull { it.name }
        ?.resolve("android.jar")
        ?: error("Android SDK platform for ${android.compileSdkVersion} is not installed")

    dependsOn(
        app.tasks.named("compileDebugKotlin"),
        app.tasks.named("compileDebugUnitTestKotlin")
    )
    mainClass.set("com.sanibonani.save.simulation.SimulationCliKt")
    classpath = files(
        app.layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        app.layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),
        app.layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
        app.layout.buildDirectory.dir("intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
        androidJar,
        app.configurations.getByName("debugUnitTestRuntimeClasspath").incoming.artifactView {
            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        }.files
    )
    providers.gradleProperty("args").orNull?.trim()?.split(" ")?.firstOrNull()
        ?.takeIf(String::isNotBlank)?.let { args(it) }
}
