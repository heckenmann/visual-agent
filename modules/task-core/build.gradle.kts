plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    jacoco
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

repositories { mavenCentral() }
dependencies {
    api(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}
kotlin {
    jvmToolchain(24)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Werror")
    }
}
tasks.test { useJUnitPlatform() }
ktlint {
    version.set(
        libs.versions.ktlint.core
            .get(),
    )
}
