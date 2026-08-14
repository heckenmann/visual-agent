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
        ":providers:check",
        ":tools:check",
        "verifyCentralizedVersions",
        "verifyModuleDependencies",
    )
}

tasks.named("build") {
    dependsOn(":application:build", ":ui:build", ":providers:build", ":tools:build")
}

gradle.projectsEvaluated {
    val moduleMainSourceSets =
        listOf(":application", ":ui", ":providers", ":tools").map { modulePath ->
            project(modulePath).extensions.getByType<SourceSetContainer>().getByName("main")
        }
    val moduleTestSourceSets =
        listOf(":application", ":ui", ":providers", ":tools").map { modulePath ->
            project(modulePath).extensions.getByType<SourceSetContainer>().getByName("test")
        }
    tasks.named<Test>("test") {
        dependsOn(":application:testClasses", ":ui:testClasses", ":providers:testClasses")
        useJUnitPlatform()
        mustRunAfter(":application:databaseTest", ":ui:databaseTest")
        workingDir = rootProject.projectDir
        systemProperty("visualagent.ollama.smoke", System.getProperty("visualagent.ollama.smoke", "false"))
        systemProperty("visualagent.codex.smoke", System.getProperty("visualagent.codex.smoke", "false"))
        jvmArgs("-Xshare:off", "-Xmx2g", "-Dkotlinx.coroutines.debug=off")
        testClassesDirs = files(moduleTestSourceSets.map { it.output.classesDirs })
        classpath = files(moduleTestSourceSets.map { it.runtimeClasspath })
        finalizedBy(tasks.jacocoTestReport)
    }
    project(":ui").tasks.named<Test>("databaseTest") {
        mustRunAfter(":application:databaseTest")
    }
    tasks.jacocoTestReport {
        dependsOn(tasks.test, ":application:databaseTest", ":ui:databaseTest")
        classDirectories.setFrom(files(moduleMainSourceSets.map { it.output.classesDirs }))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    tasks.jacocoTestCoverageVerification {
        dependsOn(tasks.test, ":application:databaseTest", ":ui:databaseTest")
        classDirectories.setFrom(files(moduleMainSourceSets.map { it.output.classesDirs }))
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
    dependsOn(":application:ktlintCheck", ":ui:ktlintCheck", ":providers:ktlintCheck", ":tools:ktlintCheck")
}

tasks.register("copyAllDependencies") {
    dependsOn(":application:copyAllDependencies")
}

tasks.register("verifyModuleDependencies") {
    group = "verification"
    description = "Verifies the directed dependency graph between Visual Agent modules."
    doLast {
        val moduleDependencies =
            setOf(":application", ":ui", ":providers", ":tools").associateWith { modulePath ->
                project(modulePath)
                    .configurations
                    .flatMap { configuration ->
                        configuration.dependencies
                            .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                            .map { it.path }
                    }.toSet()
            }
        val expectedDependencies =
            mapOf(
                ":application" to setOf(":providers", ":tools"),
                ":ui" to setOf(":application", ":providers", ":tools"),
                ":providers" to emptySet(),
                ":tools" to emptySet(),
            )
        val violations =
            expectedDependencies.flatMap { (modulePath, expected) ->
                val actual = moduleDependencies.getValue(modulePath)
                buildList {
                    if (!actual.containsAll(expected)) add("$modulePath missing ${expected - actual}")
                    if (!expected.containsAll(actual)) add("$modulePath has forbidden dependencies ${actual - expected}")
                }
            }
        check(violations.isEmpty()) { "Module dependency graph violation:\n${violations.joinToString("\n")}" }
    }
}

tasks.register("verifyCentralizedVersions") {
    group = "verification"
    description = "Prevents inline dependency and plugin versions in main-build module scripts."
    val moduleBuildFiles =
        listOf(
            "application/build.gradle.kts",
            "modules/ui/build.gradle.kts",
            "modules/providers/build.gradle.kts",
            "modules/tools/build.gradle.kts",
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
