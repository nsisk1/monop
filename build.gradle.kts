plugins {
    kotlin("jvm") version "1.9.0" // Use Kotlin 1.9.0 for compatibility
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.compose") version "1.5.10"
    application
}

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)

    // Ktor dependencies for networking
    implementation("io.ktor:ktor-server-core:2.3.3")
    implementation("io.ktor:ktor-server-netty:2.3.3")
    implementation("io.ktor:ktor-server-websockets:2.3.3")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.3")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines for asynchronous operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging (Optional)
    implementation("ch.qos.logback:logback-classic:1.4.8")
}

kotlin {
    jvmToolchain(17) // Ensures both Java & Kotlin compile to JVM 17
}

application {
    mainClass.set("MainKt")
}
