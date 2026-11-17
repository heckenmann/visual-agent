package de.heckenmann.visualagent.release.smoke

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the POSIX smoke runner against actual fixture processes and package-manager commands. */
@DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX package runner")
class PosixSmokeTest {
    @TempDir
    lateinit var root: Path

    private val repository: Path get() = Path.of(System.getProperty("release.tools.repository"))
    private val script: Path get() = repository.resolve("scripts/smoke-release-posix.sh")

    @Test
    fun `jar smoke waits for X readiness creates isolated data and stops owned processes`() {
        executable("Xvfb", "echo 77 >&3; trap 'exit 0' TERM; while :; do sleep 0.1; done")
        executable(
            "java",
            """
            test "${'$'}DISPLAY" = :77 || exit 11
            test "${'$'}1" = -jar || exit 12
            test "${'$'}2" = "${'$'}FIXTURE_ARTIFACT" || exit 13
            test "${'$'}HOME" = "${'$'}PWD/home" || exit 14
            mkdir -p server-data/workspace
            echo H2 > server-data/visual-agent.db.mv.db
            echo "${'$'}PWD" > "${'$'}FIXTURE_ROOT/work-directory"
            echo VISUAL_AGENT_DESKTOP_READY
            trap 'echo stopped > "${'$'}FIXTURE_ROOT/stopped"; exit 0' TERM
            while :; do sleep 0.1; done
            """.trimIndent(),
        )
        val artifact = Files.writeString(root.resolve("artifact with spaces.jar"), "synthetic")
        val result =
            run(
                listOf("bash", script.toString(), "linux", "jar", artifact.toString()),
                mapOf(
                    "FIXTURE_ARTIFACT" to artifact.toString(),
                ),
            )
        assertEquals(0, result.first, result.second)
        assertTrue(result.second.contains("Verified linux jar startup"), result.second)
        assertTrue(Files.exists(root.resolve("stopped")))
        assertTrue(Files.notExists(Path.of(Files.readString(root.resolve("work-directory")).trim())))
    }

    @Test
    fun `application exit before readiness fails promptly and cleans temporary directory`() {
        executable("Xvfb", "echo 12 >&3; trap 'exit 0' TERM; while :; do sleep 0.1; done")
        executable("java", "echo \"${'$'}PWD\" > \"${'$'}FIXTURE_ROOT/work-directory\"; echo Failed startup; exit 7")
        val artifact = Files.writeString(root.resolve("artifact.jar"), "synthetic")
        val result = run(listOf("bash", script.toString(), "linux", "jar", artifact.toString()))
        assertEquals(1, result.first, result.second)
        assertTrue(result.second.contains("exited before readiness"), result.second)
        assertTrue(result.second.contains("Failed startup"), result.second)
        assertTrue(Files.notExists(Path.of(Files.readString(root.resolve("work-directory")).trim())))
    }

    @Test
    fun `disk image detach retries only busy failures with bounded exponential delays`() {
        executable(
            "hdiutil",
            """
            echo call >> "${'$'}FIXTURE_ROOT/calls"
            if test "${'$'}FIXTURE_FAILURE" = busy; then
                test -f "${'$'}FIXTURE_ROOT/attempt" && exit 0
                touch "${'$'}FIXTURE_ROOT/attempt"
                echo 'Resource busy' >&2; exit 16
            fi
            echo 'Invalid device' >&2; exit 4
            """.trimIndent(),
        )
        executable("sleep", "echo \"${'$'}1\" >> \"${'$'}FIXTURE_ROOT/delays\"")
        assertEquals(0, function("smoke_detach /dev/disk2", mapOf("FIXTURE_FAILURE" to "busy")).first)
        assertEquals(2, Files.readAllLines(root.resolve("calls")).size)
        assertEquals(listOf("1"), Files.readAllLines(root.resolve("delays")))
        Files.delete(root.resolve("calls"))
        val failure = function("smoke_detach /dev/disk2", mapOf("FIXTURE_FAILURE" to "other"))
        assertEquals(4, failure.first, failure.second)
        assertEquals(1, Files.readAllLines(root.resolve("calls")).size)
    }

    @Test
    fun `native launchers come from package metadata and ambiguous inventories fail`() {
        executable("apt-get", "echo \"${'$'}*\" >> \"${'$'}FIXTURE_ROOT/package-calls\"")
        executable("dnf", "echo \"${'$'}*\" >> \"${'$'}FIXTURE_ROOT/package-calls\"")
        executable("dpkg-query", "printf '%s\\n' \"${'$'}FIXTURE_INVENTORY\"")
        executable("rpm", "printf '%s\\n' \"${'$'}FIXTURE_INVENTORY\"")
        listOf("deb", "rpm").forEach { kind ->
            val result =
                function(
                    "smoke_linux_command $kind /release.$kind; printf '%s' \"${'$'}{smoke_command[0]}\"",
                    mapOf(
                        "FIXTURE_INVENTORY" to "/opt/lib/app.jar\n/opt/bin/Visual Agent",
                    ),
                )
            assertEquals(0, result.first, result.second)
            assertEquals("/opt/bin/Visual Agent", result.second)
        }
        listOf("/opt/lib/app.jar", "/a/bin/Visual Agent\n/b/bin/Visual Agent").forEach {
            val result = function("smoke_linux_command deb /release.deb", mapOf("FIXTURE_INVENTORY" to it))
            assertEquals(1, result.first, result.second)
            assertTrue(result.second.contains("Expected one installed launcher"), result.second)
        }
    }

    @Test
    fun `invalid artifact and platform combinations fail before installation`() {
        val artifact = Files.writeString(root.resolve("artifact"), "data")
        listOf(
            listOf("linux", "dmg", artifact.toString()),
            listOf("macos", "rpm", artifact.toString()),
            listOf("linux", "jar", root.resolve("missing").toString()),
        ).forEach {
            assertEquals(1, run(listOf("bash", script.toString()) + it).first)
        }
    }

    @Test
    fun `invalid disk image contents are detached even when detach itself fails`() {
        executable(
            "hdiutil",
            """
            if test "${'$'}1" = attach; then echo '/dev/disk2 GUID_partition_scheme'; exit 0; fi
            echo detach >> "${'$'}FIXTURE_ROOT/detach-calls"
            echo 'Invalid device' >&2; exit 4
            """.trimIndent(),
        )
        val artifact = Files.writeString(root.resolve("artifact.dmg"), "data")
        val result = run(listOf("bash", script.toString(), "macos", "dmg", artifact.toString()))
        assertEquals(1, result.first, result.second)
        assertTrue(result.second.contains("did not contain a .app bundle"), result.second)
        assertTrue(result.second.contains("Invalid device"), result.second)
        assertEquals(listOf("detach"), Files.readAllLines(root.resolve("detach-calls")))
    }

    @Test
    fun `display server that exits without readiness fails and is cleaned up`() {
        executable("Xvfb", "exit 0")
        executable("java", "exit 99")
        val artifact = Files.writeString(root.resolve("artifact.jar"), "data")
        val result = run(listOf("bash", script.toString(), "linux", "jar", artifact.toString()))
        assertEquals(1, result.first, result.second)
        assertTrue(result.second.contains("without publishing a display number"), result.second)
    }

    @Test
    fun `owned process group shutdown leaves unrelated background jobs alive`() {
        val result =
            function(
                """
                set -m
                (trap 'exit 0' TERM; while :; do sleep 0.1; done) & owned=${'$'}!
                sleep 60 & unrelated=${'$'}!
                trap 'kill -TERM -- -"${'$'}unrelated" 2>/dev/null || true; wait "${'$'}unrelated" 2>/dev/null || true' EXIT
                smoke_stop "${'$'}owned" Application
                kill -0 "${'$'}unrelated"
                """.trimIndent(),
            )
        assertEquals(0, result.first, result.second)
    }

    @Test
    fun `uninstall failure is reported while owned scratch data is removed`() {
        executable("apt-get", "exit 6")
        val result =
            function(
                """
                smoke_repository=${'$'}FIXTURE_ROOT
                smoke_work=${'$'}FIXTURE_ROOT/build/visual-agent-smoke.fixture
                mkdir -p "${'$'}smoke_work"
                smoke_installed=deb
                trap smoke_cleanup EXIT
                """.trimIndent(),
            )
        assertEquals(1, result.first, result.second)
        assertTrue(Files.notExists(root.resolve("build/visual-agent-smoke.fixture")))
    }

    @Test
    fun `startup timeout fails and cleanup terminates the owned process and removes scratch data`() {
        val result =
            function(
                """
                set -m
                smoke_repository=${'$'}FIXTURE_ROOT
                smoke_work=${'$'}FIXTURE_ROOT/build/visual-agent-smoke.timeout
                mkdir -p "${'$'}smoke_work"
                : > "${'$'}smoke_work/application.log"
                (trap 'exit 0' TERM; while :; do /bin/sleep 0.1; done) & smoke_app_pid=${'$'}!
                echo "${'$'}smoke_app_pid" > "${'$'}FIXTURE_ROOT/owned-pid"
                trap smoke_cleanup EXIT
                sleep() { SECONDS=${'$'}((SECONDS + 181)); }
                smoke_wait_ready
                """.trimIndent(),
            )
        assertEquals(1, result.first, result.second)
        assertTrue(result.second.contains("Readiness marker was not emitted within 180 seconds"), result.second)
        assertTrue(Files.notExists(root.resolve("build/visual-agent-smoke.timeout")))
        val pid = Files.readString(root.resolve("owned-pid")).trim().toLong()
        assertTrue(ProcessHandle.of(pid).isEmpty || !ProcessHandle.of(pid).get().isAlive)
    }

    @Test
    fun `forced shutdown kills a stubborn owned process and remains a failed check`() {
        val result =
            function(
                """
                set -m
                (trap '' TERM; touch "${'$'}FIXTURE_ROOT/started"; while :; do /bin/sleep 0.1; done) & owned=${'$'}!
                trap 'kill -KILL -- -"${'$'}owned" 2>/dev/null || true; wait "${'$'}owned" 2>/dev/null || true' EXIT
                while test ! -f "${'$'}FIXTURE_ROOT/started"; do /bin/sleep 0.01; done
                echo "${'$'}owned" > "${'$'}FIXTURE_ROOT/owned-pid"
                sleep() { SECONDS=${'$'}((SECONDS + 31)); }
                smoke_stop "${'$'}owned" Application
                """.trimIndent(),
            )
        assertEquals(1, result.first, result.second)
        assertTrue(result.second.contains("did not stop after SIGTERM"), result.second)
        val pid = Files.readString(root.resolve("owned-pid")).trim().toLong()
        assertTrue(ProcessHandle.of(pid).isEmpty || !ProcessHandle.of(pid).get().isAlive)
    }

    @Test
    fun `native package failures display and retain the original jpackage diagnostics`() {
        val workflow = Files.readString(repository.resolve(".github/workflows/release.yml"))
        val block = Regex("(?s)      - name: Show native packaging failure logs\\n(.*?)(?=\\n      - name: Preserve)").find(workflow)
        assertTrue(block != null)
        assertTrue(block.value.contains("if: failure()"))
        val body =
            block.value
                .substringAfter("        run: |\n")
                .lineSequence()
                .joinToString("\n") { it.removePrefix("          ") }
        val logs = Files.createDirectories(root.resolve("modules/desktop/build/compose/logs/createDistributable"))
        Files.writeString(logs.resolve("jpackage-err.txt"), "A specific packaging failure\n")
        val result = run(listOf("bash", "-c", body))
        assertEquals(0, result.first, result.second)
        assertTrue(result.second.contains("A specific packaging failure"), result.second)
        assertTrue(Regex("name: Preserve native packaging failure logs\\n\\s+if: failure\\(\\)").containsMatchIn(workflow))
        assertTrue(workflow.contains("name: packaging-logs-${'$'}{{ matrix.platform }}"))
    }

    private fun function(
        body: String,
        environment: Map<String, String> = emptyMap(),
    ): Pair<Int, String> = run(listOf("bash", "-c", "source \"${'$'}1\"; $body", "fixture", script.toString()), environment)

    private fun executable(
        name: String,
        body: String,
    ) {
        val bin = Files.createDirectories(root.resolve("bin"))
        val target = Files.writeString(bin.resolve(name), "#!/usr/bin/env bash\nset -eu\n$body\n")
        check(target.toFile().setExecutable(true))
    }

    private fun run(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val log = root.resolve("command.log")
        val builder = ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile())
        builder.environment().putAll(
            mapOf("PATH" to "${root.resolve("bin")}:${System.getenv("PATH")}", "FIXTURE_ROOT" to root.toString()) + environment,
        )
        val process = builder.start()
        process.outputStream.close()
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Smoke fixture exceeded timeout")
            return process.exitValue() to Files.readString(log)
        } finally {
            if (process.isAlive) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
        }
    }
}
