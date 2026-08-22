import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

plugins {
    base
    java
    jacoco
}

group = "de.heckenmann.visualagent"
version = libs.versions.visual.agent.get()

repositories {
    mavenCentral()
}

tasks.named("check") {
    dependsOn(
        ":application:check",
        ":ui:check",
        ":protocol:check",
        ":desktop:check",
        ":agent-core:check",
        ":provider-core:check",
        ":provider-standard:check",
        ":providers:check",
        ":provider-openai-codex:check",
        ":tool-standard:check",
        ":tool-javascript:check",
        ":tools:check",
        "verifyCentralizedVersions",
        "verifyModuleDependencies",
    )
}

tasks.named("build") {
    dependsOn(
        ":application:build",
        ":ui:build",
        ":protocol:build",
        ":desktop:build",
        ":agent-core:build",
        ":provider-core:build",
        ":provider-standard:build",
        ":providers:build",
        ":provider-openai-codex:build",
        ":tool-standard:build",
        ":tool-javascript:build",
        ":tools:build",
    )
}

gradle.projectsEvaluated {
    val moduleMainSourceSets =
        listOf(":application", ":ui", ":protocol", ":desktop", ":agent-core", ":provider-core", ":provider-standard", ":provider-openai-codex", ":providers", ":tool-standard", ":tool-javascript", ":tools").map { modulePath ->
            project(modulePath).extensions.getByType<SourceSetContainer>().getByName("main")
        }
    val moduleTestSourceSets =
        listOf(":application", ":ui", ":protocol", ":desktop", ":agent-core", ":provider-core", ":provider-standard", ":provider-openai-codex", ":providers", ":tool-standard", ":tool-javascript", ":tools").map { modulePath ->
            project(modulePath).extensions.getByType<SourceSetContainer>().getByName("test")
        }
    tasks.named<Test>("test") {
        dependsOn(
            ":application:testClasses",
            ":ui:testClasses",
            ":protocol:testClasses",
            ":desktop:testClasses",
            ":agent-core:testClasses",
            ":provider-core:testClasses",
            ":provider-standard:testClasses",
            ":providers:testClasses",
            ":provider-openai-codex:testClasses",
            ":tool-standard:testClasses",
            ":tool-javascript:testClasses",
        )
        useJUnitPlatform {
            excludeTags("database", "de.heckenmann.visualagent.testsupport.DatabaseTestCategory")
        }
        // The aggregate test task produces JaCoCo execution data consumed by the root
        // coverage verification. Reusing its cached result can pair stale execution data
        // with current classes and make the coverage gate nondeterministic.
        outputs.cacheIf { false }
        mustRunAfter(":application:databaseTest")
        workingDir = rootProject.projectDir
        systemProperty("visualagent.ollama.smoke", System.getProperty("visualagent.ollama.smoke", "false"))
        systemProperty("visualagent.codex.smoke", System.getProperty("visualagent.codex.smoke", "false"))
        jvmArgs("-Xshare:off", "-Xmx2g", "-Dkotlinx.coroutines.debug=off")
        testClassesDirs = files(moduleTestSourceSets.map { it.output.classesDirs })
        classpath = files(moduleTestSourceSets.map { it.runtimeClasspath })
        finalizedBy(tasks.jacocoTestReport)
    }
    tasks.jacocoTestReport {
        dependsOn(tasks.test, ":application:databaseTest")
        executionData(
            layout.buildDirectory.file("jacoco/test.exec"),
            project(":application").layout.buildDirectory.file("jacoco/databaseTest.exec"),
        )
        classDirectories.setFrom(
            files(
                moduleMainSourceSets.map { sourceSet ->
                    sourceSet.output.classesDirs.map { classesDir ->
                        fileTree(classesDir) {
                            // Protobuf generates transport implementation classes; coverage belongs
                            // to the handwritten protocol adapters, not generated builders/accessors.
                            exclude("de/heckenmann/visualagent/protocol/v1/**")
                            // Remote transfer adapters require live HTTP/FTP/SSH endpoints; their
                            // bounded service orchestration and validation are covered by unit tests.
                            exclude("de/heckenmann/visualagent/workspace/WorkspaceDownloadTransport*")
                            exclude("de/heckenmann/visualagent/workspace/WorkspaceScpTransport*")
                        }
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
        dependsOn(tasks.test, ":application:databaseTest")
        executionData(
            layout.buildDirectory.file("jacoco/test.exec"),
            project(":application").layout.buildDirectory.file("jacoco/databaseTest.exec"),
            project(":ui").layout.buildDirectory.file("jacoco/databaseTest.exec"),
        )
        classDirectories.setFrom(
            files(
                moduleMainSourceSets.map { sourceSet ->
                    sourceSet.output.classesDirs.map { classesDir ->
                        fileTree(classesDir) {
                            // Keep generated protobuf classes out of the aggregate quality gate.
                            exclude("de/heckenmann/visualagent/protocol/v1/**")
                            // Remote transfer adapters require live HTTP/FTP/SSH endpoints; their
                            // bounded service orchestration and validation are covered by unit tests.
                            exclude("de/heckenmann/visualagent/workspace/WorkspaceDownloadTransport*")
                            exclude("de/heckenmann/visualagent/workspace/WorkspaceScpTransport*")
                        }
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
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.register("ktlintCheck") {
    dependsOn(
        ":application:ktlintCheck",
        ":ui:ktlintCheck",
        ":protocol:ktlintCheck",
        ":desktop:ktlintCheck",
        ":agent-core:ktlintCheck",
        ":provider-core:ktlintCheck",
        ":provider-standard:ktlintCheck",
        ":provider-openai-codex:ktlintCheck",
        ":tool-standard:ktlintCheck",
        ":tool-javascript:ktlintCheck",
    )
}

tasks.register("copyAllDependencies") {
    dependsOn(":application:copyAllDependencies")
}

tasks.register("verifyModuleDependencies") {
    group = "verification"
    description = "Verifies the directed dependency graph between Visual Agent modules."
    doLast {
        val moduleDependencies =
            setOf(":application", ":ui", ":protocol", ":desktop", ":agent-core", ":provider-core", ":provider-standard", ":provider-openai-codex", ":providers", ":tool-standard", ":tool-javascript", ":tools").associateWith { modulePath ->
                project(modulePath)
                    .configurations
                    .filter { configuration -> !configuration.name.contains("test", ignoreCase = true) }
                    .flatMap { configuration ->
                        configuration.dependencies
                            .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                            .map { it.path }
                    }.toSet()
            }
        val expectedDependencies =
            mapOf(
                ":application" to setOf(":providers", ":tools", ":protocol"),
                ":ui" to setOf(":protocol"),
                ":protocol" to emptySet(),
                ":desktop" to setOf(":ui", ":application", ":protocol"),
                ":agent-core" to emptySet(),
                ":provider-core" to setOf(":agent-core"),
                ":provider-standard" to setOf(":agent-core", ":provider-core"),
                ":provider-openai-codex" to setOf(":agent-core", ":provider-core"),
                ":providers" to setOf(":provider-standard", ":provider-openai-codex"),
                ":tool-standard" to emptySet(),
                ":tool-javascript" to setOf(":agent-core", ":tool-standard"),
                ":tools" to setOf(":tool-standard", ":tool-javascript"),
            )
        val violations =
            expectedDependencies.flatMap { (modulePath, expected) ->
                val actual = moduleDependencies.getValue(modulePath)
                buildList {
                    if (!actual.containsAll(expected)) add("$modulePath missing ${expected - actual}")
                    if (!expected.containsAll(actual)) add("$modulePath has forbidden dependencies ${actual - expected}")
                }
            }
        val forbiddenUiImports =
            Regex("(?:org\\.springframework|de\\.heckenmann\\.visualagent\\.(agent|config|error|knowledge|server|todo|workspace))")
        val uiSourceViolations =
            fileTree(project(":ui").projectDir.resolve("src/main"))
                .matching { include("**/*.kt") }
                .files
                .flatMap { source ->
                    source.readLines().mapIndexedNotNull { index, line ->
                        if (forbiddenUiImports.containsMatchIn(line)) "${source}:${index + 1}: $line" else null
                    }
                }
        check(violations.isEmpty() && uiSourceViolations.isEmpty()) {
            buildString {
                if (violations.isNotEmpty()) {
                    appendLine("Module dependency graph violation:")
                    appendLine(violations.joinToString("\n"))
                }
                if (uiSourceViolations.isNotEmpty()) {
                    appendLine("UI source must use protocol-owned types only:")
                    appendLine(uiSourceViolations.joinToString("\n"))
                }
            }
        }
    }
}

tasks.register("verifyCentralizedVersions") {
    group = "verification"
    description = "Prevents inline dependency and plugin versions in main-build module scripts."
    val moduleBuildFiles =
        listOf(
            "application/build.gradle.kts",
            "modules/ui/build.gradle.kts",
            "modules/protocol/build.gradle.kts",
            "modules/desktop/build.gradle.kts",
            "modules/agent-core/build.gradle.kts",
            "modules/provider-core/build.gradle.kts",
            "modules/providers/build.gradle.kts",
            "modules/provider-openai-codex/build.gradle.kts",
            "modules/providers-bundle/build.gradle.kts",
            "modules/tools/build.gradle.kts",
            "modules/tool-javascript/build.gradle.kts",
            "modules/tools-bundle/build.gradle.kts",
        ).map(rootProject.projectDir::resolve)
    inputs.files(moduleBuildFiles)
    doLast {
        val pluginVersion = Regex("""(?:kotlin|id)\([^)]*\)\s+version\s+\"""")
        val dependencyVersion = Regex("""\"[\w.-]+:[\w.-]+:[^\"${'$'}]+\"""")
        val projectVersion = Regex("""^\s*version\s*=\s*\"""", RegexOption.MULTILINE)
        val violations =
            moduleBuildFiles.flatMap { buildFile ->
                val content = buildFile.readText()
                buildList {
                    if (pluginVersion.containsMatchIn(content)) add("$buildFile contains an inline plugin version")
                    if (dependencyVersion.containsMatchIn(content)) add("$buildFile contains an inline dependency version")
                    if (projectVersion.containsMatchIn(content)) add("$buildFile contains an inline project version")
                }
            }
        check(violations.isEmpty()) { "Centralized version check failed:\n${violations.joinToString("\n")}" }
    }
}
