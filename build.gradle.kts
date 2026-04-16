plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.0"
    application
}

group = "de.heckenmann.visualagent"
version = "0.1.0"

repositories {
    mavenCentral()
}

buildDir = file(rootDir.resolve("build"))

val platform = when {
    System.getProperty("os.name").contains("Mac") && System.getProperty("os.arch").contains("aarch64") -> "mac-aarch64"
    System.getProperty("os.name").contains("Mac") -> "mac"
    System.getProperty("os.name").contains("Linux") -> "linux"
    System.getProperty("os.name").contains("Windows") -> "win"
    else -> "linux"
}

val javafxVersion = "21.0.2"

dependencies {
    implementation(kotlin("stdlib"))

    // JavaFX 21 - all modules needed
    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$platform")

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
    mainClass.set("de.heckenmann.visualagent.Main")

    applicationDefaultJvmArgs = listOf(
        "--module-path", rootDir.resolve("lib").toString(),
        "--add-modules", "javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media,javafx.swing,javafx.base",
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

tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
    into("lib")
}

tasks.register<Copy>("copyAllDependencies") {
    from(configurations.compileClasspath, configurations.runtimeClasspath)
    into("lib")
}

ktlint {
    version.set("0.50.0")
    android.set(false)
    outputColorName.set("RED")
}

tasks.register<JavaExec>("format") {
    group = "formatting"
    description = "Format Kotlin source files using ktlint"
    classpath = configurations.getByName("ktlint")
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F", "src/**/*.kt")
}

// Disabled - ktlint format requires classpath setup
// tasks.named("compileKotlin") {
//     dependsOn("format")
// }
