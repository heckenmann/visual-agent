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
    api(project(":provider-core"))
    implementation(project(":provider-standard"))
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.openai)
    implementation(libs.spring.context)
    implementation(libs.spring.webflux)
    implementation(libs.spring.security.oauth2.client)
    implementation(libs.spring.security.oauth2.jose)
    implementation(libs.reactor.netty.http)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.reactor.test)
}

tasks.test {
    useJUnitPlatform()
    filter { isFailOnNoMatchingTests = false }
}

tasks.register<JavaExec>("benchmarkDirectProvider") {
    group = "verification"
    description = "Measures direct-provider setup, encoding, and loopback HTTP/SSE overhead without external API calls."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("de.heckenmann.visualagent.agent.codexdirect.DirectProviderBenchmarkKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(24)) })
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
        libs.versions.ktlint.core
            .get(),
    )
    android.set(false)
}
