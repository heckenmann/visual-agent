import com.github.jk1.license.render.CsvReportRenderer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

plugins {
    base
    java
    jacoco
    alias(libs.plugins.license.report)
}

licenseReport {
    projects = (listOf(project) + subprojects).toTypedArray()
    buildScriptProjects = (listOf(project) + subprojects).toTypedArray()
    configurations = arrayOf("compileClasspath", "runtimeClasspath", "testRuntimeClasspath")
    renderers = arrayOf<ReportRenderer>(CsvReportRenderer(), InventoryHtmlReportRenderer())
}

val pluginClasspathReports =
    allprojects.map { target ->
        target.tasks.register("exportBuildPluginClasspath") {
            val report = target.layout.buildDirectory.file("reports/dependency-license/build-plugins.txt")
            doLast {
                val coordinates =
                    target.buildscript.configurations.getByName("classpath").incoming.resolutionResult.allComponents
                        .mapNotNull { component ->
                            (component.id as? ModuleComponentIdentifier)?.let { id ->
                                "${id.group}:${id.module}:${id.version}"
                            }
                        }.distinct()
                        .sorted()
                report.get().asFile.apply {
                    parentFile.mkdirs()
                    writeText(coordinates.joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

tasks.register("generateBuildPluginInventory") {
    group = "reporting"
    description = "Lists the resolved build-plugin classpath of every project."
    dependsOn(pluginClasspathReports)
    val report = layout.buildDirectory.file("reports/dependency-license/build-plugins.csv")
    doLast {
        val projectsByCoordinate =
            allprojects.flatMap { target ->
                target.layout.buildDirectory.file("reports/dependency-license/build-plugins.txt").get().asFile
                    .readLines()
                    .filter(String::isNotBlank)
                    .map { coordinate -> coordinate to target.path }
            }.groupBy({ it.first }, { it.second })
        val rows =
            projectsByCoordinate.toSortedMap().map { (coordinate, projects) ->
                "$coordinate,\"${projects.distinct().sorted().joinToString(" ")}\""
            }
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText((listOf("coordinate,projects") + rows).joinToString("\n", postfix = "\n"))
        }
    }
}

val dependencyScopeReports =
    allprojects.map { target ->
        target.tasks.register("exportDependencyScopes") {
            val report = target.layout.buildDirectory.file("reports/dependency-license/dependency-scopes.txt")
            doLast {
                val rows =
                    listOf("compileClasspath", "runtimeClasspath", "testRuntimeClasspath")
                        .flatMap { configurationName ->
                            val configuration = target.configurations.findByName(configurationName)
                            configuration?.incoming?.resolutionResult?.allComponents.orEmpty()
                                .mapNotNull { component ->
                                    (component.id as? ModuleComponentIdentifier)?.let { id ->
                                        "${id.group}:${id.module}:${id.version},${target.path},$configurationName"
                                    }
                                }
                        }.distinct()
                        .sorted()
                report.get().asFile.apply {
                    parentFile.mkdirs()
                    writeText(rows.joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

tasks.register("generateDependencyScopeInventory") {
    group = "reporting"
    description = "Lists resolved dependencies by project and classpath scope."
    dependsOn(dependencyScopeReports)
    val report = layout.buildDirectory.file("reports/dependency-license/dependency-scopes.csv")
    doLast {
        val rows =
            allprojects.flatMap { target ->
                target.layout.buildDirectory.file("reports/dependency-license/dependency-scopes.txt").get().asFile
                    .readLines()
                    .filter(String::isNotBlank)
            }.distinct()
                .sorted()
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText((listOf("coordinate,project,configuration") + rows).joinToString("\n", postfix = "\n"))
        }
    }
}

group = "de.heckenmann.visualagent"
version = libs.versions.visual.agent.get()

tasks.register("printProjectVersion") {
    group = "help"
    description = "Prints the Visual Agent version used by Gradle and release packages."
    doLast {
        println(project.version)
    }
}

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
        "verifyReactorBoundaries",
        "verifyKtlintCompilerCompatibility",
    )
}

val verificationModules =
    listOf(
        ":application",
        ":ui",
        ":protocol",
        ":desktop",
        ":agent-core",
        ":provider-core",
        ":provider-standard",
        ":provider-openai-codex",
        ":providers",
        ":tool-standard",
        ":tool-javascript",
        ":tools",
    )
val clientModules = listOf(":ui", ":protocol")

subprojects {
    tasks.withType<Test>().configureEach {
        // Compose test rules render offscreen; native launcher smoke tests run separately.
        jvmArgs("-Djava.awt.headless=true")
        environment.remove("DISPLAY")
        environment.remove("WAYLAND_DISPLAY")
    }
}

tasks.register("verifyKtlintCompilerCompatibility") {
    group = "verification"
    description = "Ensures KtLint resolves the compiler version it was built against."
    dependsOn(
        verificationModules.mapNotNull { modulePath ->
            val moduleProject = project(modulePath)
            val ktlintConfiguration = moduleProject.configurations.findByName("ktlint") ?: return@mapNotNull null
            moduleProject.tasks.register("verifyKtlintCompilerCompatibility") {
                group = "verification"
                description = "Ensures this module resolves the expected KtLint compiler version."
                doLast {
                    val compilerVersion =
                        ktlintConfiguration.incoming.resolutionResult.allComponents
                            .mapNotNull { component -> component.moduleVersion }
                            .firstOrNull { module ->
                                module.group == "org.jetbrains.kotlin" &&
                                    module.name == "kotlin-compiler-embeddable"
                            }?.version
                    val expectedVersion = rootProject.libs.versions.ktlint.kotlin.get()
                    check(compilerVersion == expectedVersion) {
                        "$modulePath resolved ${compilerVersion ?: "no compiler"}, expected $expectedVersion"
                    }
                }
            }
        },
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
        "verifyPackages",
    )
}

tasks.register("verifyPackages") {
    group = "verification"
    description = "Verifies executable JARs and the native desktop launcher."
    dependsOn(
        ":application:verifyExecutableJar",
        ":desktop:verifyExecutableJar",
        ":desktop:verifyMacOsNativeDnsResolver",
        ":desktop:verifyNativeDistributionLauncher",
    )
}

gradle.projectsEvaluated {
    val moduleProjects =
        listOf(
            ":application",
            ":ui",
            ":protocol",
            ":desktop",
            ":agent-core",
            ":provider-core",
            ":provider-standard",
            ":provider-openai-codex",
            ":providers",
            ":tool-standard",
            ":tool-javascript",
            ":tools",
        )
    val moduleMainSourceSets =
        moduleProjects.map { modulePath ->
            project(modulePath).extensions.getByType<SourceSetContainer>().getByName("main")
        }
    val moduleTestExecutionData =
        moduleProjects.map { modulePath ->
            project(modulePath).layout.buildDirectory.file("jacoco/test.exec")
        }
    tasks.named<Test>("test") {
        dependsOn(moduleProjects.map { modulePath -> "$modulePath:test" })
        // Native module test tasks execute every suite once and preserve their classpaths.
        finalizedBy(tasks.jacocoTestReport)
    }
    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        executionData(moduleTestExecutionData)
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
        dependsOn(tasks.test)
        executionData(moduleTestExecutionData)
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

tasks.register("verifyAutonomousReactorContracts") {
    group = "verification"
    description = "Keeps autonomous server execution Reactor-native."
    doLast {
        val base = file("application/src/main/kotlin/de/heckenmann/visualagent")
        val sources = fileTree(base.resolve("orchestration")).matching { include("**/*.kt") }.files +
            listOf("SubAgent.kt", "TodoToolCompletion.kt", "SubAgentJobScheduler.kt", "SubAgentExecutionControl.kt")
                .map { base.resolve("agent/$it") }
        val forbidden = Regex("kotlinx\\.coroutines|\\bsuspend\\s+fun\\b|\\.block(?:Required)?\\s*\\(|\\brunBlocking\\b")
        val violations = sources.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "$source:${index + 1}: $line" else null
            }
        }
        check(violations.isEmpty()) {
            "Autonomous server execution must retain Reactor contracts:\n" + violations.joinToString("\n")
        }
    }
}

tasks.register("verifyReactorBoundaries") {
    dependsOn("verifyAutonomousReactorContracts")
    group = "verification"
    description = "Prevents Project Reactor from leaking into UI-facing modules."
    dependsOn(
        clientModules.map { modulePath ->
            val moduleProject = project(modulePath)
            moduleProject.tasks.register("verifyReactorBoundary") {
                group = "verification"
                description = "Prevents Project Reactor from leaking into this UI-facing module."
                doLast {
                    val dependencyViolations =
                        listOf("compileClasspath", "runtimeClasspath").flatMap { configurationName ->
                            moduleProject.configurations.findByName(configurationName)?.incoming?.resolutionResult?.allComponents
                                ?.mapNotNull { component -> component.moduleVersion }
                                ?.filter { module -> module.group == "io.projectreactor" }
                                ?.map { module -> "$modulePath:$configurationName resolves ${module.group}:${module.name}:${module.version}" }
                                .orEmpty()
                        }.distinct()
                    check(dependencyViolations.isEmpty()) {
                        buildString {
                            appendLine("UI-facing modules must not resolve Project Reactor:")
                            appendLine(dependencyViolations.joinToString("\n"))
                        }
                    }
                }
            }
        },
    )
    doLast {
        val sourceViolations =
            clientModules.flatMap { modulePath ->
                fileTree(project(modulePath).projectDir.resolve("src/main"))
                    .matching { include("**/*.kt") }
                    .files
                    .flatMap { source ->
                        source.readLines().mapIndexedNotNull { index, line ->
                            if (Regex("\\breactor\\.").containsMatchIn(line)) "${source}:${index + 1}: $line" else null
                        }
                    }
            }
        check(sourceViolations.isEmpty()) {
            buildString {
                if (sourceViolations.isNotEmpty()) {
                    appendLine("UI-facing source must not import Project Reactor:")
                    appendLine(sourceViolations.joinToString("\n"))
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
