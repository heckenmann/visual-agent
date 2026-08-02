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
    dependsOn(":application:check", ":ui:check", ":providers:check", "verifyModuleDependencies")
}

tasks.named("build") {
    dependsOn(":application:build", ":ui:build", ":providers:build")
}

gradle.projectsEvaluated {
    val moduleMainSourceSets =
        listOf(":application", ":ui", ":providers").map { modulePath ->
            project(modulePath).extensions.getByType<SourceSetContainer>().getByName("main")
        }
    val moduleTestSourceSets =
        listOf(":application", ":ui", ":providers").map { modulePath ->
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
    dependsOn(":application:ktlintCheck", ":ui:ktlintCheck", ":providers:ktlintCheck")
}

tasks.register("copyAllDependencies") {
    dependsOn(":application:copyAllDependencies")
}

tasks.register("verifyModuleDependencies") {
    group = "verification"
    description = "Verifies that only :application depends on Visual Agent submodules."
    doLast {
        val leafModules = setOf(":ui", ":providers")
        val violations =
            leafModules.flatMap { modulePath ->
                project(modulePath)
                    .configurations
                    .flatMap { configuration ->
                        configuration.dependencies
                            .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                            .map { dependency -> "$modulePath -> ${dependency.path}" }
                    }
            }
        check(violations.isEmpty()) {
            "Submodules must not depend on :application or another submodule:\n${violations.joinToString("\n")}"
        }
        val applicationDependencies =
            project(":application")
                .configurations
                .flatMap { configuration ->
                    configuration.dependencies
                        .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                        .map { it.path }
                }
                .toSet()
        check(applicationDependencies.containsAll(leafModules)) {
            ":application must consume every initial submodule: missing ${leafModules - applicationDependencies}"
        }
    }
}
