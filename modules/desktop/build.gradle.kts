import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spring.boot)
    id("maven-publish")
    jacoco
}

// Keep relative application data paths stable when the Compose task is invoked from this module.
tasks.withType<org.gradle.api.tasks.JavaExec>().configureEach {
    // Compose's run task may otherwise reuse a stale UI artifact whose generated
    // classes do not match the desktop sources.
    dependsOn(":ui:jar")
    workingDir(rootProject.projectDir)
    jvmArgs("-Djava.awt.headless=false")
    if (name == "run") {
        systemProperty("spring.output.ansi.enabled", "ALWAYS")
    }
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":protocol"))
    implementation(project(":application"))
    implementation(libs.grpc.inprocess)
    implementation(libs.grpc.netty.shaded)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.spring.boot.starter)
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.tika.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.compose.ui.test.junit4.desktop)
    testImplementation(libs.mockk)
    testImplementation(project(":application"))
}

compose.desktop {
    application {
        mainClass = "de.heckenmann.visualagent.desktop.DesktopMain"
        jvmArgs += listOf("-Djava.awt.headless=false")
        nativeDistributions {
            packageName = "Visual Agent"
            packageVersion = project.version.toString()
        }
    }
}

springBoot {
    mainClass.set("de.heckenmann.visualagent.desktop.DesktopMain")
}

publishing {
    publications {
        create<MavenPublication>("masterJar") {
            groupId = project.group.toString()
            artifactId = "visual-agent-${System.getenv("VISUAL_AGENT_PLATFORM") ?: "local"}"
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
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "heckenmann/visual-agent"}")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
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
