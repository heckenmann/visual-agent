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
    implementation(project(":providers"))
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter)
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.model)
    implementation(libs.spring.context)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.reactor)
    implementation(libs.serialization.json)
    implementation(libs.kotlin.logging)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
    systemProperty("visualagent.codex.smoke", System.getProperty("visualagent.codex.smoke", "false"))
    systemProperty("visualagent.codex.smoke.executable", System.getProperty("visualagent.codex.smoke.executable", ""))
    systemProperty("visualagent.codex.smoke.model", System.getProperty("visualagent.codex.smoke.model", ""))
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

kotlin {
    jvmToolchain(24)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Werror")
    }
}

ktlint {
    version.set("1.5.0")
    android.set(false)
}
