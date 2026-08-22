import org.gradle.api.tasks.testing.Test
import java.nio.file.Files
import kotlin.io.path.extension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    jacoco
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":providers"))
    implementation(project(":provider-openai-codex"))
    implementation(project(":tools"))
    implementation(project(":protocol"))

    // Spring Boot & AI
    implementation(libs.spring.boot.starter)
    implementation(libs.grpc.inprocess)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.ollama)
    implementation(libs.spring.ai.openai)
    implementation("org.hibernate.orm:hibernate-community-dialects")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // SQLite JDBC
    implementation(libs.sqlite.jdbc)

    // Kotlinx Coroutines
    implementation(libs.coroutines.core)

    // JSON Serialization
    implementation(libs.serialization.json)

    // Workspace document analysis
    implementation(libs.pdfbox)
    implementation(libs.tika.core)
    implementation(libs.okhttp.jvm)
    implementation(libs.commons.net)
    implementation(libs.mina.sshd.scp)
    implementation(libs.spring.integration.sftp)

    // Kotlin logging (wrapper for SLF4J)
    implementation(libs.kotlin.logging)

    // Ensure compatible Logback is available at runtime (Spring Boot logging expects it)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)

    // Test
    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

val coroutinesVersion = libs.versions.coroutines.get()
val grpcVersion = libs.versions.grpc.get()
val protobufJavaVersion =
    libs.versions.protobuf.java
        .get()
val databaseTestTag = "database"
val databaseCategoryTag = "de.heckenmann.visualagent.testsupport.DatabaseTestCategory"

dependencyManagement {
    dependencies {
        listOf(
            "grpc-api",
            "grpc-context",
            "grpc-core",
            "grpc-inprocess",
            "grpc-netty-shaded",
            "grpc-protobuf",
            "grpc-protobuf-lite",
            "grpc-stub",
            "grpc-util",
        ).forEach { dependency("io.grpc:$it:$grpcVersion") }
        dependency("com.google.protobuf:protobuf-java:$protobufJavaVersion")
        dependency("org.jetbrains.kotlinx:kotlinx-coroutines-bom:$coroutinesVersion")
        dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
        dependency("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutinesVersion")
        dependency("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")
        dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
        dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:$coroutinesVersion")
    }
}

val databaseTest =
    tasks.register<Test>("databaseTest") {
        description = "Runs SQLite-backed tests serially."
        group = "verification"
        useJUnitPlatform {
            includeTags(databaseTestTag, databaseCategoryTag)
        }
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        maxParallelForks = 1
        filter {
            isFailOnNoMatchingTests = false
        }
        workingDir = rootProject.projectDir
        systemProperty("visualagent.ollama.smoke", System.getProperty("visualagent.ollama.smoke", "false"))
        systemProperty("visualagent.codex.smoke", System.getProperty("visualagent.codex.smoke", "false"))
        jvmArgs("-Xshare:off", "-Xmx2g", "-Dkotlinx.coroutines.debug=off")
        // JaCoCo execution data from this task is consumed by the root coverage gate.
        // Reusing a cached database run can pair stale execution data with current classes.
        outputs.cacheIf { false }
    }
databaseTest.configure { mustRunAfter(tasks.test) }

tasks.test {
    useJUnitPlatform {
        excludeTags(databaseTestTag, databaseCategoryTag)
    }
    filter {
        isFailOnNoMatchingTests = false
    }
    workingDir = rootProject.projectDir
    systemProperty("visualagent.ollama.smoke", System.getProperty("visualagent.ollama.smoke", "false"))
    systemProperty("visualagent.codex.smoke", System.getProperty("visualagent.codex.smoke", "false"))
    jvmArgs("-Xshare:off", "-Xmx2g", "-Dkotlinx.coroutines.debug=off")
    finalizedBy(tasks.jacocoTestReport)
}

val jacocoExcludedClasses = emptyList<String>()

tasks.jacocoTestReport {
    dependsOn(tasks.test, databaseTest)
    executionData(
        layout.buildDirectory.file("jacoco/test.exec"),
        layout.buildDirectory.file("jacoco/databaseTest.exec"),
    )
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(jacocoExcludedClasses)
                }
            },
            fileTree(project(":tools").layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude(jacocoExcludedClasses)
            },
        ),
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, databaseTest)
    executionData(
        layout.buildDirectory.file("jacoco/test.exec"),
        layout.buildDirectory.file("jacoco/databaseTest.exec"),
    )
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(jacocoExcludedClasses)
                }
            },
            fileTree(project(":tools").layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude(jacocoExcludedClasses)
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

springBoot {
    mainClass.set("de.heckenmann.visualagent.VisualAgentApplicationKt")
}

tasks.register("runServer") {
    group = "application"
    description = "Runs the standalone Spring Boot server without the Compose desktop host."
    dependsOn(tasks.named("bootRun"))
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir(rootProject.projectDir)
    systemProperty("spring.output.ansi.enabled", "ALWAYS")
}

kotlin {
    jvmToolchain(24)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Werror")
    }
}

tasks.register<Copy>("copyAllDependencies") {
    from(configurations.compileClasspath, configurations.runtimeClasspath)
    into(rootProject.projectDir.resolve("lib"))
}

ktlint {
    version.set("1.5.0")
    android.set(false)
}

val kotlinMainSourceRoots =
    listOf(
        projectDir.toPath().resolve("src/main/kotlin"),
        rootProject.projectDir.toPath().resolve("modules/ui/src/main/kotlin"),
        rootProject.projectDir.toPath().resolve("modules/providers/src/main/kotlin"),
        rootProject.projectDir.toPath().resolve("modules/provider-openai-codex/src/main/kotlin"),
        rootProject.projectDir.toPath().resolve("modules/tools/src/main/kotlin"),
    )
val kotlinSourceRoots =
    kotlinMainSourceRoots +
        listOf(
            projectDir.toPath().resolve("src/test/kotlin"),
            rootProject.projectDir.toPath().resolve("modules/ui/src/test/kotlin"),
            rootProject.projectDir.toPath().resolve("modules/providers/src/test/kotlin"),
            rootProject.projectDir.toPath().resolve("modules/provider-openai-codex/src/test/kotlin"),
            rootProject.projectDir.toPath().resolve("modules/tools/src/test/kotlin"),
        )

val generatedUseCaseResources = layout.buildDirectory.dir("generated/usecase-resources")

val generateUseCaseResources =
    tasks.register("generateUseCaseResources") {
        group = "documentation"
        description = "Packages documented Visual Agent use cases as runtime resources."
        val sourceDir = rootProject.layout.projectDirectory.dir("docs/usecases")
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
        val useCaseRoot = rootProject.projectDir.toPath().resolve("docs/usecases")
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
        kotlinMainSourceRoots
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

tasks.named("build") {
    dependsOn("ktlintFormat")
}

tasks.register("locAndPackageSizeCheck") {
    group = "verification"
    description = "Checks per-file LOC limits. Package size check intentionally disabled (see docs)."
    doLast {
        val maxLocPerFile = 300
        val fileViolations = mutableListOf<String>()

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
                                val msg = "${file.toAbsolutePath()}: $lines effective LOC (max $maxLocPerFile)"
                                fileViolations += msg
                            }
                        }
                }
            }

        if (fileViolations.isEmpty()) return@doLast

        val report =
            buildString {
                appendLine("LOC violations (blocking):")
                appendLine("File LOC violations:")
                fileViolations.forEach { appendLine(it) }
                appendLine()
                appendLine("Split the file to bring it under the limit.")
            }
        logger.error(report)
        throw GradleException("locAndPackageSizeCheck failed: ${fileViolations.size} file(s) exceed the $maxLocPerFile LOC limit.")
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
                projectDir.toPath().resolve("src/main"),
                projectDir.toPath().resolve("src/test"),
                rootProject.projectDir.toPath().resolve("modules/ui/src/main"),
                rootProject.projectDir.toPath().resolve("modules/ui/src/test"),
                rootProject.projectDir.toPath().resolve("modules/providers/src/main"),
                rootProject.projectDir.toPath().resolve("modules/providers/src/test"),
                rootProject.projectDir.toPath().resolve("modules/provider-openai-codex/src/main"),
                rootProject.projectDir.toPath().resolve("modules/provider-openai-codex/src/test"),
                rootProject.projectDir.toPath().resolve("modules/tools/src/main"),
                rootProject.projectDir.toPath().resolve("modules/tools/src/test"),
            )
        val checkedFiles =
            listOf(
                rootProject.projectDir.toPath().resolve("build.gradle.kts"),
                rootProject.projectDir.toPath().resolve("settings.gradle.kts"),
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

        kotlinMainSourceRoots
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
