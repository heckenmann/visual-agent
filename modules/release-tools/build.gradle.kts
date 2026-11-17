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
