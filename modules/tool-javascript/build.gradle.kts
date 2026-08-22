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
    val platformIsolate =
        when {
            System.getProperty("os.name").contains("linux", ignoreCase = true) &&
                System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64") -> "linux-amd64"
            System.getProperty("os.name").contains("linux", ignoreCase = true) &&
                System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64") -> "linux-aarch64"
            System.getProperty("os.name").contains("mac", ignoreCase = true) &&
                System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64") -> "darwin-amd64"
            System.getProperty("os.name").contains("mac", ignoreCase = true) &&
                System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64") -> "darwin-aarch64"
            System.getProperty("os.name").contains("windows", ignoreCase = true) &&
                System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64") -> "windows-amd64"
            else -> null
        }
    platformIsolate?.let { classifier ->
        runtimeOnly("org.graalvm.polyglot:js-isolate-$classifier:${libs.versions.graaljs.get()}")
    }
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
