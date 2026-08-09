import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spring.boot)
    id("maven-publish")
    jacoco
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

val applicationIdentityArgs =
    listOf(
        "-Dcom.apple.mrj.application.apple.menu.about.name=Visual Agent",
        "-Djava.awt.headless=false",
    )
val macApplicationArgs =
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        listOf(
            "-Xdock:name=Visual Agent",
            "-Xdock:icon=${projectDir.resolve("src/main/resources/icons/visual-agent.png").absolutePath}",
        )
    } else {
        emptyList()
    }
val applicationTestClasses = project(":application").layout.buildDirectory.dir("classes/kotlin/test")
val applicationMainJar =
    project(
        ":application",
    ).layout.buildDirectory.file("libs/application-${libs.versions.visual.agent.get()}-plain.jar")

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation(project(":application"))
    implementation(project(":providers"))
    implementation(project(":tools"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.reorderable)
    implementation(libs.infinite.canvas)
    implementation(libs.filekit.dialogs.compose)
    implementation(libs.spring.boot.starter)
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.commonmark)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.commonmark.gfm.strikethrough)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4.desktop)
    testImplementation(files(applicationTestClasses))
}

compose.desktop {
    application {
        mainClass = "de.heckenmann.visualagent.Main"
        jvmArgs += applicationIdentityArgs + macApplicationArgs
        nativeDistributions {
            packageName = "Visual Agent"
            packageVersion = project.version.toString()
        }
    }
}

springBoot {
    mainClass.set("de.heckenmann.visualagent.Main")
}

publishing {
    publications {
        create<MavenPublication>("masterJar") {
            groupId = project.group.toString()
            artifactId = "visual-agent"
            version =
                if (project.version.toString().endsWith("SNAPSHOT")) {
                    project.version.toString()
                } else {
                    val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
                    "${project.version}-master-${System.getenv("GITHUB_SHA")?.take(8) ?: "local"}-$runNumber"
                }
            artifact(tasks.bootJar)
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url =
                URI(
                    "https://maven.pkg.github.com/" +
                        "${System.getenv("GITHUB_REPOSITORY") ?: "heckenmann/visual-agent"}",
                )
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
    jvmArgs(applicationIdentityArgs + macApplicationArgs)
}

tasks.test {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
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

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.add(
        "-Xfriend-paths=${applicationMainJar.get().asFile.absolutePath}",
    )
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    dependsOn(":application:testClasses")
    compilerOptions.freeCompilerArgs.add(
        "-Xfriend-paths=${applicationMainJar.get().asFile.absolutePath}",
    )
}

ktlint {
    version.set("1.5.0")
    android.set(false)
}
