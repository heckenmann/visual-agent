import java.net.URI
import java.nio.file.Files
import kotlin.io.path.extension

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.jpa") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    jacoco
}

group = "de.heckenmann.visualagent"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

val applicationIdentityArgs =
    listOf(
        "-Dcom.apple.mrj.application.apple.menu.about.name=Visual Agent",
        // Compose Desktop runs on the JVM desktop stack and fails in headless mode during screen-density discovery.
        "-Djava.awt.headless=false",
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
    implementation(kotlin("reflect"))

    // Spring Boot & AI
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-openai")
    implementation("org.hibernate.orm:hibernate-community-dialects")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // Compose Multiplatform desktop UI
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("io.github.xingray:compose-infinite-canvas-core:0.2.0")
    implementation("io.github.vinceglb:filekit-dialogs-compose:0.14.2")

    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")

    // Kotlinx Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0") // Needed for Spring AI Flux to Flow

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Markdown parsing
    implementation("org.commonmark:commonmark:0.29.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.29.0")

    // Workspace document analysis
    implementation("org.apache.pdfbox:pdfbox:3.0.7")

    // Kotlin logging (wrapper for SLF4J)
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Ensure compatible Logback is available at runtime (Spring Boot logging expects it)
    implementation("ch.qos.logback:logback-classic:1.5.37")
    implementation("ch.qos.logback:logback-core:1.5.37")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("visualagent.ollama.smoke", System.getProperty("visualagent.ollama.smoke", "false"))
    jvmArgs("-Xshare:off")
    finalizedBy(tasks.jacocoTestReport)
}

val jacocoExcludedClasses =
    listOf(
        "**/ui/compose/*Kt*",
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(jacocoExcludedClasses)
                }
            },
        ),
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(jacocoExcludedClasses)
                }
            },
        ),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "de.heckenmann.visualagent.Main"
        jvmArgs += applicationIdentityArgs + macApplicationArgs
        nativeDistributions {
            packageName = "Visual Agent"
            packageVersion = project.version.toString()
        }
    }
}

springBoot {
    mainClass.set("de.heckenmann.visualagent.Main")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(applicationIdentityArgs + macApplicationArgs)
}

kotlin {
    jvmToolchain(24)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

publishing {
    publications {
        create<MavenPublication>("masterJar") {
            groupId = project.group.toString()
            artifactId = "visual-agent"
            version =
                if (project.version.toString().endsWith("SNAPSHOT")) {
                    project.version.toString()
                } else {
                    val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
                    "${project.version}-master-${System.getenv("GITHUB_SHA")?.take(8) ?: "local"}-$runNumber"
                }
            artifact(tasks.bootJar)
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url =
                URI(
                    "https://maven.pkg.github.com/" +
                        "${System.getenv("GITHUB_REPOSITORY") ?: "heckenmann/visual-agent"}",
                )
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
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

val generatedUseCaseResources = layout.buildDirectory.dir("generated/usecase-resources")

val generateUseCaseResources =
    tasks.register("generateUseCaseResources") {
        group = "documentation"
        description = "Packages documented Visual Agent use cases as runtime resources."
        val sourceDir = layout.projectDirectory.dir("docs/usecases")
        val outputDir = generatedUseCaseResources
        inputs.dir(sourceDir)
        outputs.dir(outputDir)
        doLast {
            val targetDir = outputDir.get().dir("usecases").asFile
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            val discoveredUseCaseFiles =
                sourceDir.asFile.listFiles { file ->
                    file.isFile && Regex("""uc_\d{7}_[a-z0-9_]+\.md""").matches(file.name)
                }
            val useCaseFiles = discoveredUseCaseFiles?.sortedBy { it.name }.orEmpty()
            useCaseFiles.forEach { file ->
                file.copyTo(targetDir.resolve(file.name), overwrite = true)
            }
            targetDir.resolve("index.txt").writeText(useCaseFiles.joinToString("\n") { it.name })
        }
    }

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateUseCaseResources)
    from(generatedUseCaseResources)
}

tasks.register("useCaseDocumentationCheck") {
    group = "verification"
    description = "Checks that every use-case document includes required traceability sections."
    doLast {
        val useCaseRoot = rootDir.toPath().resolve("docs/usecases")
        if (!Files.exists(useCaseRoot)) return@doLast
        val violations = mutableListOf<String>()
        Files.walk(useCaseRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && Regex("""uc_\d{7}_[a-z0-9_]+\.md""").matches(it.fileName.toString()) }
                .forEach { file ->
                    val content = Files.readString(file)
                    if (!content.contains("\n## Tool Calls\n")) {
                        violations += "${file.toAbsolutePath()} missing '## Tool Calls' section"
                    }
                    if (!Regex("""(?s)## Tool Calls\s+\n-.+?\n\n## Code Entry Points""").containsMatchIn(content)) {
                        violations += "${file.toAbsolutePath()} missing documented tool-call bullet before code entry points"
                    }
                }
        }
        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Use-case documentation check failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

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
        val hiddenTypeRegex =
            Regex(
                """^\s*(?:private|internal|protected)\s+(?:abstract\s+|open\s+|final\s+|sealed\s+|data\s+|enum\s+|annotation\s+)*(class|interface|object)\b""",
            )
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
                            var braceDepth = 0
                            var pendingHiddenType = false
                            val hiddenTypeDepths = mutableListOf<Int>()
                            lines.forEachIndexed { index, line ->
                                val trimmed = line.trim()
                                hiddenTypeDepths.removeAll { it > braceDepth }
                                val insideHiddenType = hiddenTypeDepths.isNotEmpty()
                                val opens = line.count { it == '{' }
                                val closes = line.count { it == '}' }
                                if ((pendingHiddenType || hiddenTypeRegex.containsMatchIn(trimmed)) && opens > 0) {
                                    hiddenTypeDepths += braceDepth + 1
                                    pendingHiddenType = false
                                } else if (hiddenTypeRegex.containsMatchIn(trimmed)) {
                                    pendingHiddenType = true
                                }
                                braceDepth += opens - closes
                                if (insideHiddenType) return@forEachIndexed
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
                                // Enforce KDoc for both explicit and implicit public API declarations.
                                // Kotlin declarations are public by default unless private/internal/protected.
                                if (declarationType == "val" || declarationType == "var") return@forEachIndexed
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
                                            if (kdocStartRegex.containsMatchIn(candidate)) return@run true
                                            if (candidate.endsWith("*/")) {
                                                var blockCursor = cursor - 1
                                                while (blockCursor >= 0) {
                                                    val blockLine = lines[blockCursor].trim()
                                                    if (blockLine.startsWith("/**")) return@run true
                                                    if (blockLine.startsWith("/*")) return@run false
                                                    blockCursor--
                                                }
                                            }
                                            return@run false
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

        fun effectiveLoc(lines: List<String>): Int {
            var inBlockComment = false
            var count = 0
            lines.forEach { line ->
                var trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                if (inBlockComment) {
                    if (trimmed.contains("*/")) {
                        trimmed = trimmed.substringAfter("*/").trim()
                        inBlockComment = false
                    } else {
                        return@forEach
                    }
                }
                while (trimmed.startsWith("/*")) {
                    if (!trimmed.contains("*/")) {
                        inBlockComment = true
                        return@forEach
                    }
                    trimmed = trimmed.substringAfter("*/").trim()
                    if (trimmed.isEmpty()) return@forEach
                }
                if (trimmed.startsWith("//")) return@forEach
                count++
            }
            return count
        }

        kotlinSourceRoots
            .filter { Files.exists(it) }
            .forEach { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) && it.extension == "kt" }
                        .forEach { file ->
                            val fileLines = Files.readAllLines(file)
                            val lines = effectiveLoc(fileLines)
                            if (lines > maxLocPerFile) {
                                fileViolations += "${file.toAbsolutePath()}: $lines effective LOC (max $maxLocPerFile)"
                            }
                            val pkg =
                                fileLines
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
    dependsOn("desktopApiUsageCheck")
    dependsOn("useCaseDocumentationCheck")
    dependsOn("jacocoTestCoverageVerification")
}

tasks.register("desktopApiUsageCheck") {
    group = "verification"
    description = "Fails when source files use desktop image or Swing integration APIs."
    doLast {
        val forbidden =
            listOf(
                "java." + "aw" + "t",
                "javax." + "swing",
                "swing" + "utilities",
                "j" + "frame",
                "j" + "panel",
                "j" + "component",
                "javax." + "image" + "io",
                "swing" + "fxutils",
                "buffered" + "image",
                "image" + "io",
                "pdfbox." + "rendering",
                "org." + "open" + "jfx",
                "java" + "fx-controls",
                "java" + "fx-fxml",
                "java" + "fx-graphics",
                "java" + "fx-base",
                "java" + "fx-swing",
                "open" + "jfx",
                "java" + "fx",
                "apple." + "aw" + "t",
            )
        val violations = mutableListOf<String>()
        val checkedRoots =
            listOf(
                rootDir.toPath().resolve("src/main"),
                rootDir.toPath().resolve("src/test"),
            )
        val checkedFiles =
            listOf(
                rootDir.toPath().resolve("build.gradle.kts"),
                rootDir.toPath().resolve("settings.gradle.kts"),
            )

        fun isAllowedBuildConfigurationLine(line: String): Boolean = line.contains("-Djava.awt.headless=false")

        checkedRoots
            .filter { Files.exists(it) }
            .forEach { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter { it.extension in setOf("kt", "java", "properties", "md") }
                        .forEach { file ->
                            Files.readAllLines(file).forEachIndexed { index, line ->
                                val lower = line.lowercase()
                                forbidden
                                    .filter(lower::contains)
                                    .forEach { token ->
                                        violations += "${file.toAbsolutePath()}:${index + 1} forbidden token '$token'"
                                    }
                            }
                        }
                }
            }
        checkedFiles
            .filter { Files.exists(it) }
            .forEach { file ->
                Files.readAllLines(file).forEachIndexed { index, line ->
                    if (isAllowedBuildConfigurationLine(line)) return@forEachIndexed
                    val lower = line.lowercase()
                    forbidden
                        .filter(lower::contains)
                        .forEach { token ->
                            violations += "${file.toAbsolutePath()}:${index + 1} forbidden token '$token'"
                        }
                }
            }
        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Desktop API usage check failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
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
