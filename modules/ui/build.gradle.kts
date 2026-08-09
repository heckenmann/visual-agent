import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springframework.boot") version "4.1.0"
    id("maven-publish")
    jacoco
}

group = "de.heckenmann.visualagent"
version = "0.1.0"

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
val applicationMainJar = project(":application").layout.buildDirectory.file("libs/application-0.1.0-plain.jar")

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
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("io.github.xingray:compose-infinite-canvas-core:0.2.0")
    implementation("io.github.vinceglb:filekit-dialogs-compose:0.14.2")
    implementation("org.springframework.boot:spring-boot-starter:4.1.0")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.commonmark:commonmark:0.29.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.29.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.29.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.29.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer:0.43.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.43.0")
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.1.0")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.11.1")
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
