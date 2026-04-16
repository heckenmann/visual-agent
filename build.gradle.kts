plugins {
    kotlin("jvm") version "1.9.21"
    application
}

group = "com.visualagent"
version = "0.1.0"

repositories {
    mavenCentral()
}

buildDir = file("${rootDir}/build")

val platform = when {
    System.getProperty("os.name").contains("Mac") && System.getProperty("os.arch").contains("aarch64") -> "mac-aarch64"
    System.getProperty("os.name").contains("Mac") -> "mac"
    System.getProperty("os.name").contains("Linux") -> "linux"
    System.getProperty("os.name").contains("Windows") -> "win"
    else -> "linux"
}

val javafxVersion = "21"

dependencies {
    implementation(kotlin("stdlib"))
    
    // JavaFX 21 with platform classifier
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$platform")
    
    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")
    
    // Ktor HTTP Client
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // Kotlinx Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
    
    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("MainKt")
    
    // JVM Args für JavaFX Module
    applicationDefaultJvmArgs = listOf(
        "--add-modules", "javafx.controls,javafx.fxml,javafx.web",
        "--add-opens", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    )
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
    }
}

// Alle Dependencies in den Projektordner kopieren
tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
    into("lib")
}

tasks.register<Copy>("copyAllDependencies") {
    from(configurations.compileClasspath, configurations.runtimeClasspath)
    into("lib")
}
