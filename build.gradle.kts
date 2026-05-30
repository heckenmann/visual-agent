plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    // id("org.jlleitschuh.gradle.ktlint") version "12.1.2" // Deactivated: conflicts with Kotlin 2.1.21
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    application
}

group = "de.heckenmann.visualagent"
version = "0.1.0"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://jitpack.io") }
}

val platform = when {
    System.getProperty("os.name").contains("Mac") && System.getProperty("os.arch").contains("aarch64") -> "mac-aarch64"
    System.getProperty("os.name").contains("Mac") -> "mac"
    System.getProperty("os.name").contains("Linux") -> "linux"
    System.getProperty("os.name").contains("Windows") -> "win"
    else -> "linux"
}

val javafxVersion = "21.0.2"
val javafxModuleArgs = listOf(
    "--module-path", rootDir.resolve("lib").toString(),
    "--add-modules", "javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media,javafx.swing,javafx.base",
)
val applicationIdentityArgs = listOf(
    "-Dapple.awt.application.name=Visual Agent",
    "-Dcom.apple.mrj.application.apple.menu.about.name=Visual Agent",
    "-Djavafx.application.name=Visual Agent",
)
val macApplicationArgs = if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
    listOf(
        "-Xdock:name=Visual Agent",
        "-Xdock:icon=${rootDir.resolve("src/main/resources/icons/visual-agent.png").absolutePath}",
    )
} else {
    emptyList()
}

dependencies {
    implementation(kotlin("stdlib"))

    // Spring Boot & AI
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter:1.0.0-M5")

    // JavaFX 21
    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-web:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-swing:$javafxVersion:$platform")

    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")

    // Kotlinx Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3") // Needed for Spring AI Flux to Flow

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Markdown parsing
    implementation("org.commonmark:commonmark:0.21.0")

    // AtlantaFX themes
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")

    // Ikonli icons
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.4.0")

    // Kotlin logging (wrapper for SLF4J)
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Ensure compatible Logback is available at runtime (Spring Boot logging expects it)
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("ch.qos.logback:logback-core:1.5.18")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("de.heckenmann.visualagent.Main")

    applicationDefaultJvmArgs = javafxModuleArgs + applicationIdentityArgs + macApplicationArgs
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(javafxModuleArgs + applicationIdentityArgs + macApplicationArgs)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.register<Copy>("copyAllDependencies") {
    from(configurations.compileClasspath, configurations.runtimeClasspath)
    into("lib")
}

/*
ktlint {
    version.set("1.0.1")
    android.set(false)
}
*/
