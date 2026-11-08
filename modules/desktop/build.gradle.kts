import de.heckenmann.visualagent.gradle.GenerateBranding
import de.heckenmann.visualagent.gradle.PrepareRuntimeLegalMaterials
import de.heckenmann.visualagent.gradle.verifyJarProjectLegalMaterials
import de.heckenmann.visualagent.gradle.verifyNativeProjectLegalMaterials
import de.heckenmann.visualagent.gradle.verifyPackagedRuntimeLegalMaterials
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.jar.JarFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    jvmArgs("--enable-native-access=ALL-UNNAMED")
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
    implementation(libs.compose.material.icons)
    implementation(libs.compose.components.resources)
    // Compose does not install Dispatchers.Main; its desktop event loop needs this runtime provider.
    runtimeOnly(libs.coroutines.swing)
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
    implementation(libs.appdirs)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.compose.ui.test.junit4.desktop)
    testImplementation(libs.mockk)
    testImplementation(project(":application"))
}

val desktopDispatcherProbeClasspath = files(sourceSets.main.get().runtimeClasspath, sourceSets.test.get().output)

// Resolve sources from the selected runtime graph, not independently selected versions.
// This is a partial evidence set; JDK/native source obligations remain open in #410.
val requiredSourceModules =
    setOf(
        "com.h2database:h2",
        "ch.qos.logback:logback-core",
        "ch.qos.logback:logback-classic",
        "net.java.dev.jna:jna",
        "org.jetbrains.skiko:skiko-awt",
    )
val runtimeSourceMaterials =
    configurations.runtimeClasspath.map { configuration ->
        configuration.incoming
            .artifactView {
                withVariantReselection()
                componentFilter { component ->
                    component is ModuleComponentIdentifier && "${component.group}:${component.module}" in requiredSourceModules
                }
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
                }
            }.files
    }

// Preserve the exact AppImage runtime source inputs used by the pinned binary.
// This is partial evidence: the upstream Alpine image did not pin all APK revisions.
val appImageRuntimeSourceArchives =
    listOf(
        Triple(
            "type2-runtime-dd6cebed.tar.gz",
            "https://codeload.github.com/AppImage/type2-runtime/tar.gz/dd6cebedcbddde9c82f89b011e8e1d40b6e43868",
            "f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba",
        ),
        Triple(
            "fuse-3.15.0.tar.xz",
            "https://github.com/libfuse/libfuse/releases/download/fuse-3.15.0/fuse-3.15.0.tar.xz",
            "70589cfd5e1cff7ccd6ac91c86c01be340b227285c5e200baa284e401eea2ca0",
        ),
        Triple(
            "squashfuse-0.5.2.tar.gz",
            "https://github.com/vasi/squashfuse/archive/0.5.2.tar.gz",
            "db0238c5981dabbd80ee09ae15387f390091668ca060a7bc38047912491443d3",
        ),
        Triple(
            "musl-1.2.5.tar.gz",
            "https://musl.libc.org/releases/musl-1.2.5.tar.gz",
            "a9a118bbe84d8764da0ea0d28b3ab3fae8477fc7e4085d90102b8596fc7c75e4",
        ),
        Triple(
            "alpine-musl-package.tar.gz",
            "https://gitlab.alpinelinux.org/alpine/aports/-/archive/efd4d5df7be0ea48d98512671f676fc5ceb14899/aports-efd4d5df7be0ea48d98512671f676fc5ceb14899-main-musl.tar.gz?path=main/musl",
            "9efdd30d79cf42ae2c336f44ca536a77353af41e0d567f865813ab361e3dfcaf",
        ),
        Triple(
            "mimalloc-2.1.7.tar.gz",
            "https://github.com/microsoft/mimalloc/archive/refs/tags/v2.1.7.tar.gz",
            "0eed39319f139afde8515010ff59baf24de9e47ea316a315398e8027d198202d",
        ),
        Triple(
            "alpine-mimalloc-package.tar.gz",
            "https://gitlab.alpinelinux.org/alpine/aports/-/archive/5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39/aports-5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39-community-mimalloc2.tar.gz?path=community/mimalloc2",
            "595484cc18a6c383783c277e5d4425caa7bab1174e72f7377d9be16ec3c36a0d",
        ),
        Triple(
            "zstd-1.5.6.tar.gz",
            "https://github.com/facebook/zstd/archive/v1.5.6.tar.gz",
            "30f35f71c1203369dc979ecde0400ffea93c27391bfd2ac5a9715d2173d92ff7",
        ),
        Triple(
            "alpine-zstd-package.tar.gz",
            "https://gitlab.alpinelinux.org/alpine/aports/-/archive/5c2ddf18f193dafdd25ae7391d96c7e30766eecd/aports-5c2ddf18f193dafdd25ae7391d96c7e30766eecd-main-zstd.tar.gz?path=main/zstd",
            "b6119204a1b1211edeeb84d30abb9490f2876e05546a33a83c376696bec54123",
        ),
        Triple(
            "zlib-1.3.1.tar.gz",
            "https://www.zlib.net/fossils/zlib-1.3.1.tar.gz",
            "9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23",
        ),
        Triple(
            "alpine-zlib-package.tar.gz",
            "https://gitlab.alpinelinux.org/alpine/aports/-/archive/4c2722e7ecc1c5e3ba882429044a101497121223/aports-4c2722e7ecc1c5e3ba882429044a101497121223-main-zlib.tar.gz?path=main/zlib",
            "11df2b1f95a9f3acaf7a51f7a4efea480afa1a5fb82fa3e2595f7944289237fa",
        ),
    )
val appImageRuntimeSourceDirectory =
    layout.buildDirectory.dir("reports/dependency-license/appimage-runtime-sources")
val appImageRuntimeSourceFiles =
    appImageRuntimeSourceArchives.map { (name, _, _) -> appImageRuntimeSourceDirectory.get().file(name).asFile }
val liberationFontSourceArchive =
    Triple(
        "liberation-fonts-2.1.5.tar.gz",
        "https://github.com/liberationfonts/liberation-fonts/files/7261483/liberation-fonts-2.1.5.tar.gz",
        "fb22c2109dcbb25202aeb6202c0a58fd2e536dbf7c72879c4bbfa2b8a9a75d60",
    )
val liberationFontSourceArchiveFile =
    layout.buildDirectory.file("reports/dependency-license/liberation-font-source/${liberationFontSourceArchive.first}")
val validateRuntimeSourceMaterials =
    tasks.register("validateRuntimeSourceMaterials") {
        group = "verification"
        description = "Fails before downloads when a selected runtime source artifact is unavailable."
        doLast {
            check(runtimeSourceMaterials.get().files.size == requiredSourceModules.size) {
                "Missing required selected-library source artifacts; source evidence is incomplete."
            }
        }
    }
val zuluSourcePackage =
    Triple(
        "zulu27-ca-src_27.0.1.0.3-5_amd64.deb",
        "https://repos.azul.com/zulu/deb/pool/main/z/zulu/27/zulu27-ca-src_27.0.1.0.3-5_amd64.deb",
        "c803bd7fa69e94c8f1a7370e00dd98212480d8648d6e27eed06c27b97bd4b6b4",
    )
val zuluSourcePackageFile =
    layout.buildDirectory.file("reports/dependency-license/zulu-source-package/${zuluSourcePackage.first}")
val prepareZuluSourcePackage =
    tasks.register("prepareZuluSourcePackage") {
        group = "distribution"
        description = "Collects the checksum-pinned supplemental Zulu Java source package."
        inputs.property("sourcePackage", zuluSourcePackage)
        outputs.file(zuluSourcePackageFile)
        doLast {
            downloadVerifiedFile(
                zuluSourcePackage.second,
                zuluSourcePackageFile.get().asFile,
                zuluSourcePackage.third,
            )
        }
    }
val librarySourceMaterialsDirectory =
    layout.buildDirectory.dir("reports/dependency-license/library-source-materials")
val releaseSourceMaterialsDirectory =
    layout.buildDirectory.dir("reports/dependency-license/release-source-materials")
val prepareLiberationFontSourceMaterial =
    tasks.register("prepareLiberationFontSourceMaterial") {
        group = "distribution"
        description = "Collects the checksum-pinned source archive for the embedded Liberation font."
        dependsOn(validateRuntimeSourceMaterials)
        inputs.property("sourceArchive", liberationFontSourceArchive)
        outputs.file(liberationFontSourceArchiveFile)
        doLast {
            downloadVerifiedFile(
                liberationFontSourceArchive.second,
                liberationFontSourceArchiveFile.get().asFile,
                liberationFontSourceArchive.third,
            )
        }
    }
val prepareAppImageRuntimeSourceMaterials =
    tasks.register("prepareAppImageRuntimeSourceMaterials") {
        group = "distribution"
        description = "Collects checksum-pinned, partial AppImage runtime source evidence."
        dependsOn(validateRuntimeSourceMaterials)
        inputs.property("sourceArchives", appImageRuntimeSourceArchives)
        outputs.files(appImageRuntimeSourceFiles)
        doLast {
            appImageRuntimeSourceArchives.zip(appImageRuntimeSourceFiles).forEach { (archive, target) ->
                downloadVerifiedFile(archive.second, target, archive.third)
            }
        }
    }

tasks.register<Sync>("prepareLibrarySourceMaterials") {
    group = "distribution"
    description = "Collects partial source evidence for selected runtime libraries; does not approve redistribution."
    dependsOn(prepareAppImageRuntimeSourceMaterials, prepareLiberationFontSourceMaterial)
    from(runtimeSourceMaterials) { into("archives") }
    from(appImageRuntimeSourceFiles) { into("archives/appimage-runtime") }
    from(liberationFontSourceArchiveFile) { into("archives/liberation-fonts") }
    from(rootProject.layout.projectDirectory.dir("docs/licensing/third-party")) { into("legal") }
    from(rootProject.layout.projectDirectory.file("docs/licensing/source-delivery.md"))
    into(librarySourceMaterialsDirectory)
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.register<Sync>("prepareReleaseSourceMaterials") {
    group = "distribution"
    description = "Adds the supplemental pinned Zulu source package once for the release review archive."
    dependsOn("prepareLibrarySourceMaterials", prepareZuluSourcePackage)
    from(librarySourceMaterialsDirectory)
    from(zuluSourcePackageFile) { into("archives/zulu-jdk") }
    into(releaseSourceMaterialsDirectory)
}

val generateDesktopDispatcherProbeClasspath =
    tasks.register<WriteProperties>("generateDesktopDispatcherProbeClasspath") {
        destinationFile.set(layout.buildDirectory.file("generated/dispatcher-probe/classpath.properties"))
        encoding = "UTF-8"
        property("classpath", Callable { desktopDispatcherProbeClasspath.asPath })
        dependsOn(desktopDispatcherProbeClasspath)
    }

tasks.withType<Test>().configureEach {
    dependsOn(generateDesktopDispatcherProbeClasspath)
    val probeClasspathFile = generateDesktopDispatcherProbeClasspath.flatMap { it.destinationFile }
    jvmArgumentProviders.add(
        org.gradle.process.CommandLineArgumentProvider {
            listOf("-Dvisualagent.desktop.dispatcherProbeClasspathFile=${probeClasspathFile.get().asFile.absolutePath}")
        },
    )
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

val generatedBranding = rootProject.tasks.named<GenerateBranding>("generateBranding")
val generatedBrandingDirectory = generatedBranding.flatMap { task -> task.outputDirectory }
// Compose queries icon paths while configuring run; createDistributable separately requires the generated content.
val generatedIconDirectory = generatedBranding.get().outputDirectory.locationOnly
val generatedUiIcon = generatedIconDirectory.map { dir -> dir.file("ui/icons/visual-agent.png") }
val generatedWindowsIcon = generatedIconDirectory.map { dir -> dir.file("packaging/visual-agent.ico") }
val generatedMacOsIcon = generatedIconDirectory.map { dir -> dir.file("packaging/visual-agent.icns") }

// Compose processes resources from a registered custom directory as one resource set.
// Include the existing splash/onboarding images alongside the build-generated icon,
// otherwise the image loader cannot find the original files at runtime.
val bundledComposeResources = layout.buildDirectory.dir("generated/bundled-compose-resources")
val prepareBundledComposeResources =
    tasks.register<Sync>("prepareBundledComposeResources") {
        group = "build"
        description = "Bundles the existing Compose images together with the generated application icon."
        dependsOn(generatedBranding)
        from(layout.projectDirectory.dir("src/main/composeResources"))
        from(generatedBrandingDirectory.map { dir -> dir.dir("compose") })
        into(bundledComposeResources)
        doFirst {
            val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
            listOf("startup_background_light.png", "startup_background_dark.png").forEach { name ->
                val file = layout.projectDirectory.file("src/main/composeResources/files/$name").asFile
                check(file.isFile && file.inputStream().use { it.readNBytes(8) }.contentEquals(pngSignature)) {
                    "Invalid $name: the splash backgrounds must be PNG files (run git lfs pull if necessary)."
                }
            }
        }
    }

compose.resources {
    customDirectory(
        sourceSetName = "main",
        directoryProvider = prepareBundledComposeResources.map { bundledComposeResources.get() },
    )
}

// JDK-8380634 fixes zero-major macOS app versions in jpackage 27; do not rewrite the release version.
// Kotlin's stable compiler still targets JVM 24, but compilation, tests and native runtime share JDK 27.
val desktopJavaVersion =
    libs.versions.java.toolchain
        .get()
        .toInt()
val desktopJavaHome =
    javaToolchains
        .launcherFor {
            languageVersion.set(JavaLanguageVersion.of(desktopJavaVersion))
            vendor.set(JvmVendorSpec.AZUL)
        }.get()
        .metadata.installationPath.asFile

val prepareRuntimeLegalMaterials =
    tasks.register<PrepareRuntimeLegalMaterials>("prepareRuntimeLegalMaterials") {
        group = "distribution"
        description = "Retains source-JDK legal files and exact public build provenance in native packages."
        jdkHome.set(desktopJavaHome)
        projectLicense.set(rootProject.layout.projectDirectory.file("LICENSE"))
        outputDirectory.set(layout.buildDirectory.dir("generated/runtime-legal-resources"))
    }

val prepareDistributionLegalResources =
    tasks.register<Sync>("prepareDistributionLegalResources") {
        group = "distribution"
        description = "Combines runtime and curated third-party legal texts before native signing."
        into(layout.buildDirectory.dir("generated/distribution-legal-resources"))
        from(prepareRuntimeLegalMaterials)
        from(rootProject.layout.projectDirectory.dir("docs/licensing/third-party")) {
            into("common/legal/third-party")
        }
    }

compose.desktop {
    application {
        dependsOn("prepareDistributionLegalResources")
        javaHome = desktopJavaHome.absolutePath
        mainClass = "de.heckenmann.visualagent.desktop.DesktopMain"
        jvmArgs +=
            listOfNotNull(
                "-Djava.awt.headless=false",
                "--enable-native-access=ALL-UNNAMED",
                when {
                    System.getProperty("os.name").contains("win", ignoreCase = true) -> "-Dvisual-agent.package.type=windows-msi"
                    System.getProperty("os.name").contains("mac", ignoreCase = true) -> "-Dvisual-agent.package.type=macos-dmg"
                    else -> null
                },
            )
        nativeDistributions {
            appResourcesRootDir.set(layout.dir(prepareDistributionLegalResources.map { it.destinationDir }))
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
                "jdk.crypto.ec",
                "jdk.jfr",
                "jdk.jartool",
                "jdk.management",
                "jdk.net",
                "jdk.security.auth",
                "jdk.unsupported",
            )
            packageName = "Visual Agent"
            packageVersion =
                project.version
                    .toString()
                    .substringBefore('-')
                    .substringBefore('+')
            macOS {
                bundleID = "de.heckenmann.visualagent"
                iconFile.set(generatedMacOsIcon)
                dockName = "Visual Agent"
            }
            windows {
                iconFile.set(generatedWindowsIcon)
            }
            linux {
                iconFile.set(generatedUiIcon)
                packageName = "visual-agent"
            }
        }
    }
}

val installPackagedKeytool =
    tasks.register("installPackagedKeytool") {
        group = "distribution"
        description = "Adds the JDK keytool launcher to the trimmed application runtime."
        dependsOn("createRuntimeImage")
        doLast {
            val runtimeHome =
                layout.buildDirectory
                    .dir("compose/tmp/main/runtime")
                    .get()
                    .asFile
            val osName = System.getProperty("os.name")
            val isWindows = osName.startsWith("Windows", ignoreCase = true)
            val launcherName = if (isWindows) "keytool.exe" else "keytool"
            val source = desktopJavaHome.resolve("bin/$launcherName")
            check(source.isFile) { "The configured JDK does not provide $launcherName: $source" }
            val target = runtimeHome.resolve("bin/$launcherName")
            target.parentFile.mkdirs()
            Files.copy(source.toPath(), target.toPath(), REPLACE_EXISTING)
            check(target.setExecutable(true, false) || target.canExecute()) { "Could not make packaged keytool executable: $target" }
            val result =
                ProcessBuilder(target.absolutePath, "-help")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor()
            check(result == 0) { "The packaged keytool launcher failed with exit code $result." }
        }
    }

installPackagedKeytool.configure {
    mustRunAfter("createRuntimeImage")
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    dependsOn(generatedBranding, installPackagedKeytool)
    // A task dependency alone does not invalidate a previously signed app image.
    inputs.dir(prepareDistributionLegalResources.map { it.destinationDir }).withPropertyName("distributionLegalMaterials")
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
        // Development runs intentionally opt into repository-local data. Packaged
        // applications do not inherit this Gradle task environment and use the
        // server-side platform data resolver instead.
        environment(
            "VISUAL_AGENT_DB_PATH",
            System.getProperty("visual-agent.db.path")
                ?: rootProject.file("data/visual-agent.db").absolutePath,
        )
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
                desktopJavaHome.resolve("bin/jpackage"),
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
                desktopJavaHome.resolve("bin/jpackage"),
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
        manifest.attributes["Enable-Native-Access"] = "ALL-UNNAMED"
        setClasspath(files(desktopMainOutput, desktopRuntimeClasspath))
        from(rootProject.file("LICENSE")) { into("META-INF/visual-agent/legal/visual-agent") }
        from(rootProject.file("docs/licensing/third-party")) { into("META-INF/visual-agent/legal/third-party") }
    }

tasks.register("generateDistributionArtifactInventory") {
    group = "reporting"
    description = "Maps packaged JAR names to resolved desktop runtime artifacts."
    dependsOn("verifyNativeDistributionLauncher")
    val report = layout.buildDirectory.file("reports/dependency-license/packaged-artifacts.csv")
    doLast {
        val runtimeArtifacts =
            configurations
                .getByName("runtimeClasspath")
                .incoming.artifacts.artifacts
                .filter { artifact -> artifact.file.isFile && artifact.file.extension == "jar" }
        val artifactsByPackagingHash =
            runtimeArtifacts.groupBy { artifact -> auditPackagingHash(artifact.file) }
        val projectJar =
            tasks
                .named<org.gradle.jvm.tasks.Jar>("jar")
                .get()
                .archiveFile
                .get()
                .asFile
        val bundledJdkJar = desktopJavaHome.resolve("lib/jrt-fs.jar")
        val packageRoot =
            layout.buildDirectory
                .dir("compose/binaries/main/app")
                .get()
                .asFile
        val rows =
            packageRoot
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "jar" }
                .map { file ->
                    val packagingHash = file.name.removeSuffix(".jar").substringAfterLast('-')
                    val artifacts =
                        artifactsByPackagingHash[packagingHash].orEmpty().filter { artifact ->
                            file.name == "${artifact.file.name.removeSuffix(".jar")}-$packagingHash.jar"
                        }
                    val transformedArtifactCandidates =
                        if (artifacts.isEmpty()) {
                            // Compose removes the host-native entry from platform JARs, changing their content hash.
                            runtimeArtifacts.filter { candidate ->
                                val packagedPrefix = "${candidate.file.name.removeSuffix(".jar")}-"
                                val packagedSuffix = file.name.removePrefix(packagedPrefix).removeSuffix(".jar")
                                file.name.startsWith(packagedPrefix) && packagedSuffix.matches(Regex("[0-9a-f]{1,32}"))
                            }
                        } else {
                            emptyList()
                        }
                    val artifact = (artifacts.ifEmpty { transformedArtifactCandidates }).singleOrNull()
                    val component = artifact?.id?.componentIdentifier
                    val packagedPrefix = "${projectJar.nameWithoutExtension}-"
                    val packagedSuffix = file.name.removePrefix(packagedPrefix).removeSuffix(".jar")
                    val isProjectJar =
                        file.name.startsWith(packagedPrefix) && packagedSuffix.matches(Regex("[0-9a-f]{1,32}"))
                    val isBundledJdkJar =
                        file.name == "jrt-fs.jar" && file.invariantSeparatorsPath.contains("/runtime/")
                    val resolvedFile =
                        artifact?.file
                            ?: when {
                                isProjectJar -> projectJar
                                isBundledJdkJar -> bundledJdkJar
                                else -> null
                            }
                    val coordinate =
                        if (component is ModuleComponentIdentifier) {
                            "${component.group}:${component.module}:${component.version}"
                        } else if (artifact != null) {
                            component?.displayName ?: "UNMATCHED"
                        } else if (isProjectJar) {
                            "project ':desktop'"
                        } else if (isBundledJdkJar) {
                            "bundled-jdk:$desktopJavaVersion:jrt-fs"
                        } else {
                            if (artifacts.size > 1 || transformedArtifactCandidates.size > 1) "AMBIGUOUS" else "UNMATCHED"
                        }
                    val sourceHash = resolvedFile?.takeIf(File::isFile)?.let { auditHash(it, "SHA-256") }.orEmpty()
                    val packageHash = auditHash(file, "SHA-256")
                    "${file.relativeTo(
                        packageRoot,
                    ).invariantSeparatorsPath},$coordinate,${resolvedFile?.name.orEmpty()},$sourceHash,$packageHash"
                }.sorted()
                .toList()
        val unresolvedArtifacts = rows.filter { row -> ",UNMATCHED," in row || ",AMBIGUOUS," in row }
        check(unresolvedArtifacts.isEmpty()) {
            "Unable to map packaged JARs to unique resolved runtime artifacts:\n${unresolvedArtifacts.joinToString("\n")}"
        }
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText((listOf("packagePath,coordinate,resolvedFile,sourceSha256,packageSha256") + rows).joinToString("\n", postfix = "\n"))
        }
    }
}

// Compose encodes each MD5 byte without zero-padding when naming packaged JARs.
private fun auditPackagingHash(file: File): String =
    auditDigest(file, "MD5").joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16) }

private fun auditHash(
    file: File,
    algorithm: String,
): String = auditDigest(file, algorithm).joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun auditDigest(
    file: File,
    algorithm: String,
): ByteArray {
    val digest = MessageDigest.getInstance(algorithm)
    DigestInputStream(file.inputStream(), digest).use { input ->
        input.transferTo(OutputStream.nullOutputStream())
    }
    return digest.digest()
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
        dependsOn("createDistributable", generatedBranding)
        val desktopEntry = linuxJpackageResources.file("de.heckenmann.VisualAgent.desktop")
        val appRun = linuxJpackageResources.file("AppRun")
        inputs.dir(linuxApplicationImage)
        inputs.file(desktopEntry)
        inputs.file(appRun)
        inputs.file(generatedUiIcon)
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
                from(generatedUiIcon)
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
    if (target.isFile && auditHash(target, "SHA-256") == expectedSha256) return

    val temporaryFile = Files.createTempFile(target.parentFile.toPath(), target.name, ".download")
    try {
        URI(url).toURL().openStream().use { source ->
            Files.newOutputStream(temporaryFile).use(source::copyTo)
        }
        val actualSha256 = auditHash(temporaryFile.toFile(), "SHA-256")
        check(actualSha256 == expectedSha256) {
            "Downloaded file checksum mismatch: expected $expectedSha256 but received $actualSha256."
        }
        Files.move(temporaryFile, target.toPath(), REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporaryFile)
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
        dependsOn(generatedBranding)
        inputs.file(generatedUiIcon)
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
            val icon = generatedUiIcon.get().asFile.absolutePath
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
        dependsOn(generatedBranding)
        inputs.files(generatedMacOsIcon, generatedWindowsIcon, generatedUiIcon)
        doLast {
            verifyIconFile(generatedMacOsIcon.get().asFile, byteArrayOf(0x69, 0x63, 0x6e, 0x73))
            verifyIconFile(generatedWindowsIcon.get().asFile, byteArrayOf(0, 0, 1, 0))
            verifyIconFile(
                generatedUiIcon.get().asFile,
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
            val runtimeHome =
                when {
                    osName.equals("Mac OS X", ignoreCase = true) ->
                        distributionDirectory.resolve("$appName.app/Contents/runtime/Contents/Home")
                    osName.startsWith("Windows", ignoreCase = true) -> distributionDirectory.resolve("$appName/runtime")
                    else -> distributionDirectory.resolve("$appName/lib/runtime")
                }
            val keytool = runtimeHome.resolve("bin/${if (osName.startsWith("Windows", ignoreCase = true)) "keytool.exe" else "keytool"}")
            check(keytool.isFile && keytool.canExecute()) { "Native distribution keytool is missing or not executable: $keytool" }
            val runtimeVersion =
                runtimeHome
                    .resolve("release")
                    .readLines()
                    .first { it.startsWith("JAVA_VERSION=") }
                    .substringAfter('=')
                    .trim('"')
            check(JavaVersion.toVersion(runtimeVersion).majorVersion == desktopJavaVersion.toString()) {
                "Native distribution runtime $runtimeVersion does not match project toolchain $desktopJavaVersion."
            }
            val resources =
                when {
                    osName.equals("Mac OS X", ignoreCase = true) -> distributionDirectory.resolve("$appName.app/Contents/app/resources")
                    osName.startsWith("Windows", ignoreCase = true) -> distributionDirectory.resolve("$appName/app/resources")
                    else -> distributionDirectory.resolve("$appName/lib/app/resources")
                }
            val provenance = verifyPackagedRuntimeLegalMaterials(resources, runtimeHome, desktopJavaHome, rootProject.file("LICENSE"))
            verifyNativeProjectLegalMaterials(resources, rootProject.projectDir)
            layout.buildDirectory.file("reports/dependency-license/runtime-provenance.properties").get().asFile.apply {
                parentFile.mkdirs()
                writeText(provenance.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
            }
        }
    }

tasks.matching { it.name in setOf("packageDmg", "packageMsi", "packageLinuxDeb", "packageLinuxRpm", "packageAppImage") }.configureEach {
    dependsOn(verifyNativeDistributionLauncher)
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
                verifyJarProjectLegalMaterials(archive, rootProject.projectDir)
                val attributes = archive.manifest.mainAttributes
                check(attributes.getValue("Main-Class") == "org.springframework.boot.loader.launch.JarLauncher") {
                    "Desktop boot JAR must use Spring Boot's JarLauncher as Main-Class"
                }
                check(attributes.getValue("Start-Class") == "de.heckenmann.visualagent.desktop.DesktopMain") {
                    "Desktop boot JAR must declare DesktopMain as Start-Class"
                }
                check(attributes.getValue("Enable-Native-Access") == "ALL-UNNAMED") {
                    "Desktop boot JAR must authorize the Skia native renderer"
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
    dependsOn(verifyNativeDistributionIcons)
    dependsOn(verifyLinuxDesktopEntry)
}

kotlin {
    jvmToolchain(desktopJavaVersion)
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

tasks.named<BaseKtLintCheckTask>("runKtlintCheckOverMainSourceSet") {
    setSource(fileTree("src/main/kotlin"))
}

tasks.named<BaseKtLintCheckTask>("runKtlintFormatOverMainSourceSet") {
    setSource(fileTree("src/main/kotlin"))
}
