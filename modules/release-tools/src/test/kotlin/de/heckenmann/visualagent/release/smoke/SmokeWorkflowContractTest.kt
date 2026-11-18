package de.heckenmann.visualagent.release.smoke

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Preserves independent release checks, offscreen gates, native process checks, and toolchain contracts. */
class SmokeWorkflowContractTest {
    private val repository = Path.of(System.getProperty("release.tools.repository"))

    private fun read(name: String): String = Files.readString(repository.resolve(name))

    @Test
    fun `release contracts run independently with the complete Kotlin test suites`() {
        val workflow = read(".github/workflows/test.yml")
        val jobs =
            Regex("(?ms)^  ([a-z-]+):\\n(.*?)(?=^  [a-z-]+:|\\z)").findAll(workflow).associate {
                it.groupValues[1] to
                    it.groupValues[2]
            }
        val contracts = jobs.getValue("release-contracts")
        assertTrue(contracts.contains("./gradlew :release-tools:test :release-tools:releaseIntegrationTest"))
        assertFalse(Regex("(?m)^    needs:").containsMatchIn(contracts))
        listOf("cache-read-only: true", "gradle/actions/wrapper-validation", "java-version: ${'$'}{{ env.ZULU_JDK_VERSION }}").forEach {
            assertTrue(contracts.contains(it))
        }
        assertTrue(workflow.contains("ZULU_JDK_VERSION: \"27.0.1.0.3+1\""))
        assertTrue(jobs.getValue("test").contains("timeout-minutes: 15"))
    }

    @Test
    fun `only real desktop launches use a virtual display while quality gates remain headless`() {
        for (file in listOf("test.yml", "release.yml")) {
            val workflow = read(".github/workflows/$file")
            val commands = Regex("(?m)^        run: (.*\\./gradlew (?:ktlintCheck|verifyPackages).*)$").findAll(workflow).toList()
            assertTrue(commands.isNotEmpty(), file)
            commands.forEach {
                assertFalse(it.value.lowercase().contains("xvfb"))
                assertTrue(it.value.contains("env -u DISPLAY -u WAYLAND_DISPLAY ./gradlew"))
            }
        }
        val build = read("build.gradle.kts")
        listOf(
            "tasks.withType<Test>().configureEach",
            "jvmArgs(\"-Djava.awt.headless=true\")",
            "environment.remove(\"DISPLAY\")",
            "environment.remove(\"WAYLAND_DISPLAY\")",
            "providers.environmentVariable(\"GITHUB_ACTIONS\")",
            "providers.gradleProperty(\"testLifecycleLogging\")",
            "events(\"started\", \"passed\", \"skipped\", \"failed\")",
            "showStandardStreams = false",
        ).forEach {
            assertTrue(build.contains(it), it)
        }
        val workflow = read(".github/workflows/test.yml")
        assertTrue(workflow.contains("local launch_command=(\"${'$'}java_cmd\" -Djava.awt.headless=true)"))
        assertTrue(
            Regex(
                "if \\[\\[ \"\\${'$'}require_spring_startup\" == \"false\" \\]\\]; then\\s+launch_command=\\(xvfb-run -a \"\\${'$'}java_cmd\" -Dskiko.renderApi=SOFTWARE\\)",
            ).containsMatchIn(workflow),
        )
    }

    @Test
    fun `native smoke retains menu dependencies and Windows process tree checks`() {
        val release = read(".github/workflows/release.yml")
        val ubuntu = Regex("(?s)- name: Install Ubuntu desktop runtime dependencies\\n(.*?)(?=\\n      - name:)").find(release)
        assertTrue(ubuntu != null)
        assertTrue(ubuntu.value.contains("gnome-menus"))
        assertTrue(Regex("apt-get install --yes [^\\n]*\\bprocps\\b").containsMatchIn(ubuntu.value))
        val fedora = Regex("(?s)- name: Install Fedora desktop runtime dependencies\\n(.*?)(?=\\n      - name:)").find(release)
        assertTrue(fedora != null)
        assertTrue(Regex("dnf install --assumeyes [^\\n]*\\bprocps-ng\\b").containsMatchIn(fedora.value))
        assertTrue(release.contains("./scripts/test-windows-smoke-processes.ps1"))
        val ci = read(".github/workflows/test.yml")
        assertTrue(ci.contains("name: Windows smoke process contract"))
        assertTrue(ci.contains("./scripts/test-windows-smoke-processes.ps1"))
        val windows = read("scripts/smoke-release-windows.ps1")
        listOf(
            "Get-SmokeProcessIds -RootProcessId ${'$'}app.Id",
            "Close-SmokeWindows -Processes",
            "throw \"The application did not exit cleanly",
            "Remove-SmokeWorkDirectory -Path ${'$'}work",
        ).forEach {
            assertTrue(windows.contains(it))
        }
        val windowsProcessHelpers = read("scripts/windows-smoke-processes.ps1")
        listOf(
            "function Remove-SmokeWorkDirectory",
            "${'$'}attemptLimit = 15",
            "Remove-Item -LiteralPath ${'$'}Path -Recurse -Force -ErrorAction Stop",
            "Start-Sleep -Milliseconds 1000",
            "if (${'$'}attempt -eq ${'$'}attemptLimit) { throw }",
        ).forEach {
            assertTrue(windowsProcessHelpers.contains(it))
        }
        assertTrue(release.contains("bash scripts/smoke-release-posix.sh"))
    }

    @Test
    fun `release tag is applied globally without shell interpolation and native version uses numeric core`() {
        val workflow = read(".github/workflows/release.yml")
        assertTrue(
            workflow
                .substringBefore(
                    "\njobs:",
                ).contains("ORG_GRADLE_PROJECT_releaseTag: ${'$'}{{ github.event_name == 'push' && github.ref_name || '' }}"),
        )
        assertFalse(workflow.contains("expected_tag=\"v${'$'}{gradle_version}\""))
        assertFalse(Regex("run:.*\\${'$'}\\{\\{ github.event.release.tag_name").containsMatchIn(workflow))
        assertTrue(
            Regex(
                "packageVersion\\s*=\\s*project\\.version\\s*\\.toString\\(\\)\\s*\\.substringBefore\\('-'\\)\\s*\\.substringBefore\\('\\+'\\)",
            ).containsMatchIn(read("modules/desktop/build.gradle.kts")),
        )
    }

    @Test
    fun `all builds use the shared toolchain and supported bytecode target`() {
        val catalog = read("gradle/libs.versions.toml")
        assertTrue(catalog.contains("java-toolchain = \"27\""))
        assertTrue(catalog.contains("java-bytecode = \"24\""))
        val desktop = read("modules/desktop/build.gradle.kts")
        listOf(
            "languageVersion.set(JavaLanguageVersion.of(desktopJavaVersion))",
            "jvmToolchain(desktopJavaVersion)",
            "majorVersion == desktopJavaVersion.toString()",
        ).forEach {
            assertTrue(desktop.contains(it))
        }
        assertTrue(
            Regex(
                "val desktopJavaVersion\\s*=\\s*libs\\s*\\.versions\\s*\\.java\\s*\\.toolchain\\s*\\.get\\(\\)\\s*\\.toInt\\(\\)",
            ).containsMatchIn(desktop),
        )
        assertFalse(desktop.contains("installerVersions"))
        val builds =
            listOf(repository.resolve("application/build.gradle.kts")) +
                Files.list(repository.resolve("modules")).use { paths ->
                    paths.map { it.resolve("build.gradle.kts") }.filter { Files.isRegularFile(it) }.toList()
                }
        builds.forEach { file ->
            val text = Files.readString(file)
            assertFalse(text.contains("jvmToolchain(24)"), file.toString())
            assertFalse(text.contains("JavaLanguageVersion.of(24)"), file.toString())
            if (text.contains("jvmToolchain(")) {
                assertTrue(
                    Regex(
                        "JvmTarget\\s*\\.fromTarget\\(\\s*libs\\.versions\\.java\\.bytecode\\s*\\.get\\(\\),?\\s*\\)",
                    ).containsMatchIn(text),
                    file.toString(),
                )
                assertTrue(text.contains("-Xjdk-release=${'$'}{libs.versions.java.bytecode.get()}"), file.toString())
            }
        }
        Files.list(repository.resolve(".github/workflows")).use { paths ->
            paths.filter { it.toString().endsWith(".yml") }.forEach { file ->
                val text = Files.readString(file)
                assertTrue(Regex("java-version:\\s*\"(\\d+)\"").findAll(text).all { it.groupValues[1] == "27" }, file.toString())
                assertTrue(Regex("distribution:\\s*(\\S+)").findAll(text).all { it.groupValues[1] == "zulu" }, file.toString())
            }
        }
        val build = read("build.gradle.kts")
        assertTrue(build.contains("options.release.set(javaBytecodeVersion)"))
        assertTrue(build.contains("dependsOn(verifyJavaToolchains)"))
    }
}
