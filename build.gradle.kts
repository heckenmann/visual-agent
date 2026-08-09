import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test

plugins {
    base
    java
    jacoco
}

group = "de.heckenmann.visualagent"
version = "0.1.0"

repositories {
    mavenCentral()
}

tasks.named("check") {
    dependsOn(":application:check", ":ui:check", ":providers:check", ":tools:check", "verifyModuleDependencies")
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
        workingDir = rootProject.projectDir
        systemProperty("visualagent.ollama.smoke", System.getProperty("visualagent.ollama.smoke", "false"))
        systemProperty("visualagent.codex.smoke", System.getProperty("visualagent.codex.smoke", "false"))
        jvmArgs("-Xshare:off", "-Xmx2g", "-Dkotlinx.coroutines.debug=off")
        testClassesDirs = files(moduleTestSourceSets.map { it.output.classesDirs })
        classpath = files(moduleTestSourceSets.map { it.runtimeClasspath })
        finalizedBy(tasks.jacocoTestReport)
    }
    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        classDirectories.setFrom(files(moduleMainSourceSets.map { it.output.classesDirs }))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    tasks.jacocoTestCoverageVerification {
        dependsOn(tasks.test)
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
