package de.heckenmann.visualagent.release.packaging

import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Path

/** Runs isolated fixture projects with the configured JDK, current Gradle, and shared dependency cache. */
internal fun fixtureGradle(
    project: Path,
    arguments: List<String>,
    environment: Map<String, String> = emptyMap(),
): GradleRunner =
    GradleRunner
        .create()
        .withProjectDir(project.toFile())
        .withTestKitDir(Path.of(System.getProperty("release.tools.testkit")).toFile())
        .withEnvironment(System.getenv().filterKeys { !it.startsWith("ORG_GRADLE_PROJECT_") } + environment)
        .withArguments(
            listOf(
                "--offline",
                "--quiet",
                "--gradle-user-home",
                System.getProperty("release.tools.gradle.home"),
                "-Dorg.gradle.java.home=${System.getProperty("java.home")}",
            ) + arguments,
        )
