plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ktlint)
    jacoco
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

repositories {
    mavenCentral()
}

dependencies {
    api(project(":agent-core"))
    api(project(":tool-standard"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.serialization.json)
    implementation(libs.reactor.core)
    implementation(libs.graal.polyglot)
    implementation(libs.graal.js)
    implementation(platform(libs.spring.boot.bom))
    compileOnly(libs.spring.context)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
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
        libs
            .versions
            .ktlint
            .core
            .get(),
    )
    android.set(false)
}
