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

repositories {
    mavenCentral()
}

dependencies {
    api(project(":model-core"))
    api(project(":task-core"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.logging)
    implementation(platform(libs.spring.boot.bom))
    compileOnly(libs.spring.context)
    implementation(libs.serialization.json)
    implementation(libs.reactor.core)
    implementation(libs.dnsjava)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.reactor.test)
}

tasks.test {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
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
