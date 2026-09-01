import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.net.URI
import java.security.MessageDigest
import java.util.jar.JarFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spring.boot)
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

val linuxApplicationImage =
    layout.buildDirectory.dir("compose/binaries/main/app/Visual Agent")
val linuxJpackageResources =
    layout.projectDirectory.dir("packaging/linux")
val linuxPackageOutput =
    layout.buildDirectory.dir("compose/binaries/main/packages")
val appImageDirectory =
    layout.buildDirectory.dir("appimage/Visual Agent.AppDir")
val appImageToolVersion = "1.9.1"
val appImageTool =
    layout.buildDirectory.file("appimage/tools/appimagetool-$appImageToolVersion-x86_64.AppImage")
val appImageOutput =
    linuxPackageOutput.map { output -> output.file("Visual-Agent-${project.version}-x86_64.AppImage") }
val appImageToolDownloadUrl =
    "https://github.com/AppImage/appimagetool/releases/download/$appImageToolVersion/appimagetool-x86_64.AppImage"
val appImageToolSha256 = "ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"
val appImageRuntimeVersion = "20251108"
val appImageRuntime =
    layout.buildDirectory.file("appimage/tools/runtime-$appImageRuntimeVersion-x86_64")
val appImageRuntimeDownloadUrl =
    "https://github.com/AppImage/type2-runtime/releases/download/$appImageRuntimeVersion/runtime-x86_64"
val appImageRuntimeSha256 = "2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d"
val developmentLinuxDesktopEntry =
    layout.buildDirectory.file("compose/desktop-integration/visualagent-development.desktop")

val runNativeApplication =
    tasks.register<Exec>("runNativeApplication") {
        group = "application"
        description = "Runs the current operating system's native Visual Agent application bundle."
        dependsOn("createDistributable")
        workingDir(rootProject.projectDir)
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

val packageLinuxDeb =
    tasks.register<Exec>("packageLinuxDeb") {
        group = "distribution"
        description = "Packages the native Visual Agent application image as a Linux DEB with desktop integration."
        dependsOn("createDistributable")
        inputs.dir(linuxJpackageResources)
        outputs.dir(linuxPackageOutput)
        onlyIf { System.getProperty("os.name", "").contains("linux", ignoreCase = true) }
        doFirst {
            commandLine(
                File(System.getProperty("java.home"), "bin/jpackage"),
                "--type",
                "deb",
                "--app-image",
                linuxApplicationImage.get().asFile,
                "--dest",
                linuxPackageOutput.get().asFile,
                "--resource-dir",
                linuxJpackageResources.asFile,
                "--linux-package-name",
                "visual-agent",
                "--linux-shortcut",
            )
        }
    }

val packageLinuxRpm =
    tasks.register<Exec>("packageLinuxRpm") {
        group = "distribution"
        description = "Packages the native Visual Agent application image as a Linux RPM with desktop integration."
        dependsOn("createDistributable")
        inputs.dir(linuxJpackageResources)
        outputs.dir(linuxPackageOutput)
        onlyIf { System.getProperty("os.name", "").contains("linux", ignoreCase = true) }
        doFirst {
            commandLine(
                File(System.getProperty("java.home"), "bin/jpackage"),
                "--type",
                "rpm",
                "--app-image",
                linuxApplicationImage.get().asFile,
                "--dest",
                linuxPackageOutput.get().asFile,
                "--resource-dir",
                linuxJpackageResources.asFile,
                "--linux-package-name",
                "visual-agent",
                "--linux-shortcut",
            )
        }
    }

// Compose 1.12 publishes metadata-only compatibility bridges under the legacy
// org.jetbrains.compose.runtime coordinates alongside AndroidX implementation
// JARs with identical file names. Spring Boot packages dependencies by file
// name, so retain the implementation artifacts and omit only the bridges.
val desktopRuntimeClasspath =
    configurations.runtimeClasspath
        .get()
        .incoming
        .artifactView {
            componentFilter { component ->
                component !is ModuleComponentIdentifier || component.group != "org.jetbrains.compose.runtime"
            }
        }.files

val desktopMainOutput =
    extensions
        .getByType<SourceSetContainer>()
        .getByName("main")
        .output

val desktopBootJar =
    tasks.named<BootJar>("bootJar") {
        setClasspath(files(desktopMainOutput, desktopRuntimeClasspath))
    }

val stageReleaseJar =
    tasks.register<Copy>("stageReleaseJar") {
        group = "distribution"
        description = "Stages the executable Visual Agent JAR for a GitHub release."
        dependsOn(desktopBootJar)
        from(desktopBootJar.flatMap { bootJar -> bootJar.archiveFile })
        into(linuxPackageOutput)
        rename { "Visual-Agent-${project.version}.jar" }
    }

val downloadAppImageTool =
    tasks.register("downloadAppImageTool") {
        group = "distribution"
        description = "Downloads the official appimagetool used to create the portable Linux package."
        outputs.file(appImageTool)
        onlyIf {
            System.getProperty("os.name", "").contains("linux", ignoreCase = true) &&
                System.getProperty("os.arch", "").equals("amd64", ignoreCase = true)
        }
        doLast {
            val target = appImageTool.get().asFile
            downloadVerifiedFile(appImageToolDownloadUrl, target, appImageToolSha256)
            check(target.setExecutable(true)) { "Could not make appimagetool executable: $target" }
        }
    }

val downloadAppImageRuntime =
    tasks.register("downloadAppImageRuntime") {
        group = "distribution"
        description = "Downloads the verified AppImage Type 2 runtime embedded in the portable Linux package."
        outputs.file(appImageRuntime)
        onlyIf {
            System.getProperty("os.name", "").contains("linux", ignoreCase = true) &&
                System.getProperty("os.arch", "").equals("amd64", ignoreCase = true)
        }
        doLast {
            downloadVerifiedFile(
                appImageRuntimeDownloadUrl,
                appImageRuntime.get().asFile,
                appImageRuntimeSha256,
            )
        }
    }

val prepareAppImage =
    tasks.register("prepareAppImage") {
        group = "distribution"
        description = "Prepares the portable Visual Agent AppDir from the native application image."
        dependsOn("createDistributable")
        val desktopEntry = linuxJpackageResources.file("de.heckenmann.VisualAgent.desktop")
        val appRun = linuxJpackageResources.file("AppRun")
        inputs.dir(linuxApplicationImage)
        inputs.file(desktopEntry)
        inputs.file(appRun)
        inputs.file(project.file("../ui/src/main/resources/icons/visual-agent.png"))
        outputs.dir(appImageDirectory)
        onlyIf {
            System.getProperty("os.name", "").contains("linux", ignoreCase = true) &&
                System.getProperty("os.arch", "").equals("amd64", ignoreCase = true)
        }
        doLast {
            val appDir = appImageDirectory.get().asFile
            delete(appDir)
            copy {
                from(linuxApplicationImage)
                into(appDir.resolve("usr/lib/visual-agent"))
            }
            copy {
                from(desktopEntry)
                into(appDir)
                filter { line -> line.replace("APPLICATION_VERSION", project.version.toString()) }
            }
            copy {
                from(appRun)
                from(project.file("../ui/src/main/resources/icons/visual-agent.png"))
                into(appDir)
                rename("visual-agent.png", "de.heckenmann.VisualAgent.png")
            }
            val appRunTarget = appDir.resolve("AppRun")
            check(appRunTarget.setExecutable(true)) { "Could not make AppRun executable: $appRunTarget" }
        }
    }

val packageAppImage =
    tasks.register<Exec>("packageAppImage") {
        group = "distribution"
        description = "Packages Visual Agent as a portable Linux AppImage."
        dependsOn(downloadAppImageTool, downloadAppImageRuntime, prepareAppImage)
        inputs.dir(appImageDirectory)
        inputs.file(appImageTool)
        inputs.file(appImageRuntime)
        outputs.file(appImageOutput)
        onlyIf {
            System.getProperty("os.name", "").contains("linux", ignoreCase = true) &&
                System.getProperty("os.arch", "").equals("amd64", ignoreCase = true)
        }
        environment("APPIMAGE_EXTRACT_AND_RUN", "1")
        environment("ARCH", "x86_64")
        doFirst {
            commandLine(
                appImageTool.get().asFile,
                "--runtime-file",
                appImageRuntime.get().asFile,
                appImageDirectory.get().asFile,
                appImageOutput.get().asFile,
            )
        }
    }

private fun downloadVerifiedFile(
    url: String,
    target: File,
    expectedSha256: String,
) {
    target.parentFile.mkdirs()
    URI(url).toURL().openStream().use { source ->
        target.outputStream().use(source::copyTo)
    }
    val actualSha256 =
        MessageDigest
            .getInstance("SHA-256")
            .digest(target.readBytes())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    check(actualSha256 == expectedSha256) {
        "Downloaded file checksum mismatch: expected $expectedSha256 but received $actualSha256."
    }
}

private fun quoteDesktopExecArgument(value: String): String =
    "\"" +
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("`", "\\`")
            .replace('$'.toString(), "\\" + '$') +
        "\""

val prepareDevelopmentLinuxDesktopEntry =
    tasks.register("prepareDevelopmentLinuxDesktopEntry") {
        group = "application"
        description = "Prepares the temporary Linux desktop entry used while running Visual Agent from Gradle."
        val template = linuxJpackageResources.file("de.heckenmann.VisualAgent.development.desktop.template")
        inputs.file(template)
        outputs.file(developmentLinuxDesktopEntry)
        doLast {
            val launcher =
                linuxApplicationImage
                    .get()
                    .asFile
                    .resolve("bin/Visual Agent")
                    .absolutePath
            val icon = project.file("../ui/src/main/resources/icons/visual-agent.png").absolutePath
            val desktopEntry = developmentLinuxDesktopEntry.get().asFile
            desktopEntry.parentFile.mkdirs()
            desktopEntry.writeText(
                template.asFile
                    .readText()
                    .replace("@EXECUTABLE@", quoteDesktopExecArgument(launcher))
                    .replace("@ICON@", icon),
            )
        }
    }

val installDevelopmentLinuxDesktopEntry =
    tasks.register<Exec>("installDevelopmentLinuxDesktopEntry") {
        group = "application"
        description = "Registers the temporary Visual Agent desktop entry so Linux taskbars can identify the native window."
        dependsOn(prepareDevelopmentLinuxDesktopEntry)
        inputs.file(developmentLinuxDesktopEntry)
        outputs.upToDateWhen { false }
        onlyIf { System.getProperty("os.name", "").contains("linux", ignoreCase = true) }
        commandLine("xdg-desktop-menu", "install", "--mode", "user", developmentLinuxDesktopEntry.get().asFile)
    }

val uninstallDevelopmentLinuxDesktopEntry =
    tasks.register<Exec>("uninstallDevelopmentLinuxDesktopEntry") {
        group = "application"
        description = "Removes the temporary Visual Agent desktop entry after a Gradle development run."
        dependsOn(prepareDevelopmentLinuxDesktopEntry)
        inputs.file(developmentLinuxDesktopEntry)
        outputs.upToDateWhen { false }
        onlyIf { System.getProperty("os.name", "").contains("linux", ignoreCase = true) }
        commandLine("xdg-desktop-menu", "uninstall", "--mode", "user", developmentLinuxDesktopEntry.get().asFile.name)
    }

runNativeApplication.configure {
    dependsOn(installDevelopmentLinuxDesktopEntry)
    finalizedBy(uninstallDevelopmentLinuxDesktopEntry)
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

val verifyLinuxDesktopEntry =
    tasks.register("verifyLinuxDesktopEntry") {
        group = "verification"
        description = "Verifies the Linux desktop launcher maps Visual Agent windows to their package entry."
        val desktopEntry = linuxJpackageResources.file("Visual Agent.desktop")
        inputs.file(desktopEntry)
        doLast {
            val entry = desktopEntry.asFile.readText()
            check("Name=APPLICATION_NAME" in entry) { "Linux desktop entry must use jpackage's application name." }
            check("Exec=APPLICATION_LAUNCHER" in entry) { "Linux desktop entry must use jpackage's native launcher." }
            check("Icon=APPLICATION_ICON" in entry) { "Linux desktop entry must use jpackage's application icon." }
            check("StartupNotify=true" in entry) { "Linux desktop entry must enable startup notification." }
            check("StartupWMClass=de-heckenmann-visualagent-desktop-DesktopMain" in entry) {
                "Linux desktop entry must match the X11 class created by DesktopMain."
            }
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
                check(archive.getJarEntry("BOOT-INF/classes/de/heckenmann/visualagent/desktop/DesktopMain.class") != null) {
                    "Desktop boot JAR must contain its configured start class"
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
    dependsOn(verifyLinuxDesktopEntry)
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
