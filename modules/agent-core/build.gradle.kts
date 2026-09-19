plugins {
    alias(libs.plugins.kotlin.jvm)
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
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
    filter { isFailOnNoMatchingTests = false }
}

kotlin {
    jvmToolchain(24)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Werror")
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
