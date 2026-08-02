plugins {
    base
}

group = "de.heckenmann.visualagent"
version = "0.1.0"

tasks.named("check") {
    dependsOn(":application:check", ":ui:check", ":providers:check", "verifyModuleDependencies")
}

tasks.named("build") {
    dependsOn(":application:build", ":ui:build", ":providers:build")
}

tasks.register("test") {
    dependsOn(":application:test", ":ui:test", ":providers:test")
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
