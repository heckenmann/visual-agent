import java.nio.file.Files
import kotlin.io.path.extension

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
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

val platform =
    when {
        System.getProperty("os.name").contains("Mac") &&
            System.getProperty("os.arch").contains("aarch64") -> "mac-aarch64"
        System.getProperty("os.name").contains("Mac") -> "mac"
        System.getProperty("os.name").contains("Linux") -> "linux"
        System.getProperty("os.name").contains("Windows") -> "win"
        else -> "linux"
    }

val javafxVersion = "21.0.2"
val javafxModuleArgs =
    listOf(
        "--module-path",
        rootDir.resolve("lib").toString(),
        "--add-modules",
        "javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media,javafx.swing,javafx.base",
    )
val applicationIdentityArgs =
    listOf(
        "-Dapple.awt.application.name=Visual Agent",
        "-Dcom.apple.mrj.application.apple.menu.about.name=Visual Agent",
        "-Djavafx.application.name=Visual Agent",
    )
val macApplicationArgs =
    if (
        System.getProperty("os.name").contains("Mac", ignoreCase = true)
    ) {
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
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.7"))
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

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
    systemProperty("visualagent.ollama.smoke", System.getProperty("visualagent.ollama.smoke", "false"))
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

ktlint {
    version.set("1.5.0")
    android.set(false)
}

val kotlinSourceRoots =
    listOf(
        rootDir.toPath().resolve("src/main/kotlin"),
        rootDir.toPath().resolve("src/test/kotlin"),
    )

tasks.register("ktlintJavadocCheck") {
    group = "verification"
    description = "Checks that public Kotlin declarations have KDoc/Javadoc comments."
    doLast {
        val visibilityRegex = Regex("""\b(private|internal|protected)\b""")
        val declarationRegex =
            Regex(
                """^\s*(?:public\s+)?(?:abstract\s+|open\s+|final\s+|sealed\s+|data\s+|enum\s+|annotation\s+|suspend\s+|tailrec\s+|infix\s+|operator\s+|inline\s+|external\s+|const\s+)*?(class|interface|object|fun|val|var)\b""",
            )
        val enumClassRegex = Regex("""^\s*(?:public\s+)?enum\s+class\b""")
        val dataClassRegex = Regex("""^\s*(?:public\s+)?data\s+class\b""")
        val sealedClassRegex = Regex("""^\s*(?:public\s+)?sealed\s+class\b""")
        val annotationClassRegex = Regex("""^\s*(?:public\s+)?annotation\s+class\b""")
        val kdocStartRegex = Regex("""^\s*/\*\*""")
        val violations = mutableListOf<String>()
        listOf(rootDir.toPath().resolve("src/main/kotlin"))
            .filter { Files.exists(it) }
            .forEach { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) && it.extension == "kt" }
                        .forEach { file ->
                            val lines = Files.readAllLines(file)
                            lines.forEachIndexed { index, line ->
                                val trimmed = line.trim()
                                if (trimmed.startsWith("@")) return@forEachIndexed
                                val isDeclaration =
                                    declarationRegex.containsMatchIn(trimmed) ||
                                        enumClassRegex.containsMatchIn(trimmed) ||
                                        dataClassRegex.containsMatchIn(trimmed) ||
                                        sealedClassRegex.containsMatchIn(trimmed) ||
                                        annotationClassRegex.containsMatchIn(trimmed)
                                if (!isDeclaration) return@forEachIndexed
                                val declarationType =
                                    declarationRegex
                                        .find(trimmed)
                                        ?.groupValues
                                        ?.getOrNull(1)
                                        .orEmpty()
                                if (visibilityRegex.containsMatchIn(trimmed)) return@forEachIndexed
                                if (trimmed.startsWith("override ")) return@forEachIndexed
                                if (declarationType == "val" || declarationType == "var") return@forEachIndexed
                                val hasExplicitPublic = Regex("""^\s*public\b""").containsMatchIn(trimmed)
                                if (!hasExplicitPublic) return@forEachIndexed
                                val hasKdoc =
                                    run {
                                        var cursor = index - 1
                                        while (cursor >= 0) {
                                            val candidate = lines[cursor].trim()
                                            if (candidate.isEmpty()) {
                                                cursor--
                                                continue
                                            }
                                            if (candidate.startsWith("@")) {
                                                cursor--
                                                continue
                                            }
                                            return@run kdocStartRegex.containsMatchIn(candidate)
                                        }
                                        return@run false
                                    }
                                if (!hasKdoc) {
                                    violations += "${file.toAbsolutePath()}:${index + 1} Missing KDoc for public declaration"
                                }
                            }
                        }
                }
            }
        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("KDoc check failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("ktlintCheck") {
    dependsOn("ktlintJavadocCheck")
    dependsOn("unusedCodeCheck")
}

tasks.register("locAndPackageSizeCheck") {
    group = "verification"
    description = "Checks per-file LOC and package size constraints."
    doLast {
        val maxLocPerFile = 300
        val maxPackageLoc = 3000
        val fileViolations = mutableListOf<String>()
        val packageLoc = linkedMapOf<String, Int>()

        kotlinSourceRoots
            .filter { Files.exists(it) }
            .forEach { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) && it.extension == "kt" }
                        .forEach { file ->
                            val lines = Files.readAllLines(file).size
                            if (lines > maxLocPerFile) {
                                fileViolations += "${file.toAbsolutePath()}: $lines LOC (max $maxLocPerFile)"
                            }
                            val pkg =
                                Files
                                    .readAllLines(file)
                                    .firstOrNull { it.trim().startsWith("package ") }
                                    ?.removePrefix("package ")
                                    ?.trim()
                                    ?: "(default)"
                            packageLoc[pkg] = (packageLoc[pkg] ?: 0) + lines
                        }
                }
            }

        val packageViolations =
            packageLoc.entries
                .filter { it.value > maxPackageLoc }
                .map { "${it.key}: ${it.value} LOC (max $maxPackageLoc)" }

        if (fileViolations.isNotEmpty() || packageViolations.isNotEmpty()) {
            logger.warn(
                buildString {
                    appendLine("LOC warnings (non-blocking):")
                    if (fileViolations.isNotEmpty()) {
                        appendLine("File LOC violations:")
                        fileViolations.forEach { appendLine(it) }
                    }
                    if (packageViolations.isNotEmpty()) {
                        appendLine("Package LOC violations:")
                        packageViolations.forEach { appendLine(it) }
                    }
                },
            )
        }
    }
}

tasks.named("check") {
    dependsOn("locAndPackageSizeCheck")
    dependsOn("unusedCodeCheck")
}

tasks.register("unusedCodeCheck") {
    group = "verification"
    description =
        "Checks for obviously unused private Kotlin declarations that can likely be removed."
    doLast {
        val privateDeclarationRegex =
            Regex(
                """^\s*private\s+(?:suspend\s+|inline\s+|tailrec\s+|infix\s+|operator\s+|const\s+|lateinit\s+|data\s+|sealed\s+|enum\s+|annotation\s+|open\s+|final\s+|abstract\s+)*?(fun|val|var|class|object|interface)\s+([A-Za-z_][A-Za-z0-9_]*)""",
            )
        val suppressUnusedRegex = Regex("""@Suppress\(\s*"unused"\s*\)""")
        val violations = mutableListOf<String>()

        listOf(rootDir.toPath().resolve("src/main/kotlin"))
            .filter { Files.exists(it) }
            .forEach { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) && it.extension == "kt" }
                        .forEach { file ->
                            val lines = Files.readAllLines(file)
                            val content = lines.joinToString("\n")
                            lines.forEachIndexed { index, line ->
                                val match = privateDeclarationRegex.find(line) ?: return@forEachIndexed
                                val name = match.groupValues[2]
                                val hasFxmlAnnotationNearby =
                                    (index downTo maxOf(0, index - 3)).any { lookback ->
                                        lines[lookback].contains("@FXML")
                                    }
                                if (hasFxmlAnnotationNearby) return@forEachIndexed
                                val hasSuppressUnusedNearby =
                                    (index downTo maxOf(0, index - 3)).any { lookback ->
                                        suppressUnusedRegex.containsMatchIn(lines[lookback])
                                    }
                                if (hasSuppressUnusedNearby) return@forEachIndexed
                                val occurrences = Regex("""\b${Regex.escape(name)}\b""").findAll(content).count()
                                if (occurrences <= 1) {
                                    violations +=
                                        "${file.toAbsolutePath()}:${index + 1} Private declaration '$name' appears unused"
                                }
                            }
                        }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Unused-code check failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}
