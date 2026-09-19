
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:3.5.0")
    implementation("io.mockk:mockk:1.13.12")
}

sourceSets {
    main {
        java.srcDirs(
            "src/main/kotlin",
            "../simulation/src/main/kotlin",
            "../domain/src/main/java"
        )
    }
}

tasks.register<JavaExec>("run") {
    mainClass.set("com.sanibonani.save.simulation.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}
