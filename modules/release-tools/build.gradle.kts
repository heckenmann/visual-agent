plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    jacoco
}

application {
    mainClass.set("de.heckenmann.visualagent.release.ReleaseToolsMainKt")
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.jackson.core)
    implementation(libs.commons.compress)
    implementation(libs.commons.csv)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testImplementation(libs.xz)
}

// Contract tests inspect repository files beyond their compiled test classpath.
// Git excludes generated build/cache data while including newly added source files.
val contractRepository = rootProject.projectDir
val repositoryContractFiles =
    providers
        .exec {
            workingDir(contractRepository)
            commandLine("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z")
        }.standardOutput.asText
        .map { names ->
            names.split('\u0000').filter(String::isNotEmpty).map(contractRepository::resolve)
        }

tasks.withType<Test>().configureEach {
    inputs
        .files(repositoryContractFiles)
        .withPropertyName("repositoryContracts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.test {
    dependsOn(tasks.installDist)
    systemProperty(
        "release.tools.launcher",
        layout.buildDirectory
            .file("install/release-tools/bin/release-tools")
            .get()
            .asFile.absolutePath,
    )
    systemProperty("release.tools.repository", rootProject.projectDir.absolutePath)
    useJUnitPlatform { excludeTags("release-integration") }
    filter { isFailOnNoMatchingTests = false }
}

val releaseIntegrationTest =
    tasks.register<Test>("releaseIntegrationTest") {
        description = "Verifies real release source preparation and Gradle task failure contracts."
        group = "verification"
        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        dependsOn(":desktop:prepareReleaseSourceMaterials")
        // Offline TestKit builds inspect the desktop packager's runtime inputs.
        // Resolve external JARs online here without building or copying project JARs.
        inputs
            .files(
                provider {
                    project(":desktop")
                        .configurations
                        .getByName("runtimeClasspath")
                        .incoming
                        .artifactView {
                            componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                        }.files
                },
            ).withPropertyName("desktopRuntimeArtifacts")
            .withPathSensitivity(PathSensitivity.NAME_ONLY)
        systemProperty("release.tools.repository", rootProject.projectDir.absolutePath)
        systemProperty("release.tools.gradle.home", gradle.gradleUserHomeDir.absolutePath)
        systemProperty(
            "release.tools.testkit",
            layout.buildDirectory
                .dir("test-kit")
                .get()
                .asFile.absolutePath,
        )
        useJUnitPlatform { includeTags("release-integration") }
        filter { isFailOnNoMatchingTests = false }
        shouldRunAfter(tasks.test)
    }

tasks.check { dependsOn(releaseIntegrationTest) }

kotlin {
    jvmToolchain(
        libs.versions.java.toolchain
            .get()
            .toInt(),
    )
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(
                    libs.versions.java.bytecode
                        .get(),
                ),
        )
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Werror", "-Xjdk-release=${libs.versions.java.bytecode.get()}")
    }
}

ktlint {
    version.set(
        libs.versions.ktlint.core
            .get(),
    )
    android.set(false)
}
