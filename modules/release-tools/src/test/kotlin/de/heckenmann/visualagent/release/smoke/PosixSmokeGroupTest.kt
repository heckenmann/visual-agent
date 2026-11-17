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

/** Covers process inventory states that depend on the container's PID 1 reaping policy. */
@DisabledOnOs(OS.WINDOWS, disabledReason = "POSIX process inventory")
class PosixSmokeGroupTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `zombies are stopped while live descendants remain active and inventory errors fail closed`() {
        val script = Path.of(System.getProperty("release.tools.repository")).resolve("scripts/smoke-release-posix.sh")
        val body =
            """
            source "${'$'}1"
            ps() { printf '%s\n' '123 Z' '123 Z+' '456 S'; }
            if smoke_group_alive 123; then exit 11; else test "${'$'}?" = 1; fi
            ps() { printf '%s\n' '123 Z' '123 S+'; }
            smoke_group_alive 123
            ps() { return 7; }
            if smoke_group_alive 123; then exit 12; else test "${'$'}?" = 2; fi
            """.trimIndent()
        val log = root.resolve("command.log")
        val process =
            ProcessBuilder("bash", "-c", body, "fixture", script.toString())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Process inventory fixture timed out")
            assertEquals(0, process.exitValue(), Files.readString(log))
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
