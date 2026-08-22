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
    implementation(libs.graal.polyglot)
    implementation(libs.graal.js)
    compileOnly(platform(libs.spring.boot.bom))
    compileOnly(libs.spring.context)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
    filter { isFailOnNoMatchingTests = false }
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
