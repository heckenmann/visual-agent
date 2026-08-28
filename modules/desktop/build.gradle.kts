import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.util.jar.JarFile

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
        description = "Builds and runs Visual Agent through the native launcher for the current operating system."
        dependsOn("runNativeApplication")
        onlyIf { false }
    }
}

group = "de.heckenmann.visualagent"
version =
    libs.versions.visual.agent
        .get()

val macOsDnsResolverClassifier =
    if (System.getProperty("os.name").equals("Mac OS X", ignoreCase = true)) {
        when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "osx-aarch_64"
            "amd64", "x86_64" -> "osx-x86_64"
            else -> error("Unsupported macOS architecture for Netty DNS resolver: ${System.getProperty("os.arch")}")
        }
    } else {
        null
    }

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
    macOsDnsResolverClassifier?.let { classifier ->
        runtimeOnly(
            variantOf(libs.netty.resolver.dns.native.macos) {
                classifier(classifier)
            },
        )
    }
    implementation(libs.tika.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.compose.ui.test.junit4.desktop)
    testImplementation(libs.mockk)
    testImplementation(project(":application"))
}

if (macOsDnsResolverClassifier != null) {
    dependencies {
        components {
            listOf(
                "io.projectreactor.netty:reactor-netty-core",
                "io.projectreactor.netty:reactor-netty-http",
            ).forEach { reactorNettyModule ->
                withModule(reactorNettyModule) {
                    allVariants {
                        withDependencies {
                            removeIf { dependency ->
                                dependency.group == "io.netty" && dependency.name == "netty-resolver-dns-native-macos"
                            }
                        }
                    }
                }
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "de.heckenmann.visualagent.desktop.DesktopMain"
        jvmArgs += listOf("-Djava.awt.headless=false")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            modules(
                "java.compiler",
                "java.instrument",
                "java.net.http",
                "java.prefs",
                "java.rmi",
                "java.scripting",
                "java.security.jgss",
                "java.sql",
                "java.sql.rowset",
                "jdk.jfr",
                "jdk.management",
                "jdk.security.auth",
                "jdk.unsupported",
            )
            packageName = "Visual Agent"
            packageVersion = project.version.toString()
            macOS {
                bundleID = "de.heckenmann.visualagent"
                iconFile.set(project.file("packaging/icons/visual-agent.icns"))
                dockName = "Visual Agent"
            }
            windows {
                iconFile.set(project.file("packaging/icons/visual-agent.ico"))
            }
            linux {
                iconFile.set(project.file("../ui/src/main/resources/icons/visual-agent.png"))
                packageName = "visual-agent"
            }
        }
    }
}

val runNativeApplication =
    tasks.register<Exec>("runNativeApplication") {
        group = "application"
        description = "Runs the current operating system's native Visual Agent application bundle."
        dependsOn("createDistributable")
        doFirst {
            val distributionDirectory =
                layout.buildDirectory
                    .dir("compose/binaries/main/app")
                    .get()
                    .asFile
            val appName = "Visual Agent"
            when {
                System.getProperty("os.name").equals("Mac OS X", ignoreCase = true) ->
                    commandLine(
                        "open",
                        "-W",
                        "--env",
                        "JAVA_TOOL_OPTIONS=-Duser.dir=${rootProject.projectDir.absolutePath}",
                        distributionDirectory.resolve("$appName.app"),
                    )
                System.getProperty("os.name").startsWith("Windows", ignoreCase = true) ->
                    commandLine(distributionDirectory.resolve("$appName/$appName.exe"))
                else -> commandLine(distributionDirectory.resolve("$appName/bin/$appName"))
            }
        }
    }

val verifyNativeDistributionIcons =
    tasks.register("verifyNativeDistributionIcons") {
        group = "verification"
        description = "Verifies that every native distribution has its required platform icon."
        doLast {
            verifyIconFile(project.file("packaging/icons/visual-agent.icns"), byteArrayOf(0x69, 0x63, 0x6e, 0x73))
            verifyIconFile(project.file("packaging/icons/visual-agent.ico"), byteArrayOf(0, 0, 1, 0))
            verifyIconFile(
                project.file("../ui/src/main/resources/icons/visual-agent.png"),
                byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10),
            )
        }
    }

private fun verifyIconFile(
    icon: File,
    signature: ByteArray,
) {
    check(icon.isFile && icon.length() >= signature.size) { "Native distribution icon is missing or incomplete: $icon" }
    check(icon.inputStream().use { input -> input.readNBytes(signature.size).contentEquals(signature) }) {
        "Native distribution icon has an unexpected file format: $icon"
    }
}

val verifyNativeDistributionLauncher =
    tasks.register("verifyNativeDistributionLauncher") {
        group = "verification"
        description = "Verifies the current platform distribution contains a native Visual Agent launcher."
        dependsOn("createDistributable")
        doLast {
            val distributionDirectory =
                layout.buildDirectory
                    .dir("compose/binaries/main/app")
                    .get()
                    .asFile
            val appName = "Visual Agent"
            val osName = System.getProperty("os.name", "")
            val launcher =
                when {
                    osName.equals("Mac OS X", ignoreCase = true) ->
                        distributionDirectory.resolve("$appName.app/Contents/MacOS/$appName")
                    osName.startsWith("Windows", ignoreCase = true) -> distributionDirectory.resolve("$appName/$appName.exe")
                    else -> distributionDirectory.resolve("$appName/bin/$appName")
                }
            check(launcher.isFile && launcher.canExecute()) { "Native distribution launcher is missing or not executable: $launcher" }
        }
    }

springBoot {
    mainClass.set("de.heckenmann.visualagent.desktop.DesktopMain")
}

val verifyExecutableJar =
    tasks.register("verifyExecutableJar") {
        group = "verification"
        description = "Verifies that the distributable desktop boot JAR has an executable manifest."
        dependsOn(tasks.named("bootJar"))
        doLast {
            val executableJar =
                tasks
                    .named<BootJar>("bootJar")
                    .get()
                    .archiveFile
                    .get()
                    .asFile
            check(executableJar.isFile) { "Executable desktop JAR was not created: $executableJar" }
            JarFile(executableJar).use { archive ->
                val attributes = archive.manifest.mainAttributes
                check(attributes.getValue("Main-Class") == "org.springframework.boot.loader.launch.JarLauncher") {
                    "Desktop boot JAR must use Spring Boot's JarLauncher as Main-Class"
                }
                check(attributes.getValue("Start-Class") == "de.heckenmann.visualagent.desktop.DesktopMain") {
                    "Desktop boot JAR must declare DesktopMain as Start-Class"
                }
            }
        }
    }

val verifyMacOsNativeDnsResolver =
    tasks.register("verifyMacOsNativeDnsResolver") {
        group = "verification"
        description = "Verifies that the desktop boot JAR contains the matching macOS Netty DNS resolver."
        dependsOn(tasks.named("bootJar"))
        onlyIf { macOsDnsResolverClassifier != null }
        doLast {
            val executableJar =
                tasks
                    .named<BootJar>("bootJar")
                    .get()
                    .archiveFile
                    .get()
                    .asFile
            val expectedResolver = "netty-resolver-dns-native-macos-"
            val expectedClassifier = "-$macOsDnsResolverClassifier.jar"
            val unexpectedClassifier =
                if (macOsDnsResolverClassifier == "osx-aarch_64") "-osx-x86_64.jar" else "-osx-aarch_64.jar"
            JarFile(executableJar).use { archive ->
                val resolverLibraries =
                    archive
                        .entries()
                        .asSequence()
                        .map { entry -> entry.name }
                        .filter { entry -> entry.startsWith("BOOT-INF/lib/$expectedResolver") }
                        .toList()
                check(resolverLibraries.any { entry -> entry.endsWith(expectedClassifier) }) {
                    "Desktop boot JAR must contain the Netty macOS DNS resolver for $macOsDnsResolverClassifier"
                }
                check(resolverLibraries.none { entry -> entry.endsWith(unexpectedClassifier) }) {
                    "Desktop boot JAR must not contain the Netty macOS DNS resolver for the other architecture"
                }
            }
        }
    }

tasks.named("check") {
    dependsOn(verifyExecutableJar)
    dependsOn(verifyMacOsNativeDnsResolver)
    dependsOn(verifyNativeDistributionIcons)
    dependsOn(verifyNativeDistributionLauncher)
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
