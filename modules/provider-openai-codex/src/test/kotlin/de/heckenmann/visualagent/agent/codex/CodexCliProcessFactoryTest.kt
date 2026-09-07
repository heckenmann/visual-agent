package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/** Verifies bounded command execution used for Codex CLI discovery. */
class CodexCliProcessFactoryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `captures command output and exit status`() =
        runTest {
            val executable = temporaryDirectory.resolve("fake-codex")
            Files.writeString(executable, "#!/bin/sh\nprintf 'codex-cli test\\n'\n")
            check(executable.toFile().setExecutable(true)) { "Test executable permission could not be set" }

            val result = CodexCliProcessFactory().run(listOf(executable.toString()), timeoutSeconds = 5)

            assertEquals(0, result.exitCode)
            assertEquals("codex-cli test", result.stdout.text.trim())
        }

    @Test
    fun `removes provider api keys from child process environment`() =
        runTest {
            val executable = temporaryDirectory.resolve("environment-codex")
            Files.writeString(
                executable,
                "#!/bin/sh\nprintf '%s|%s' \"${'$'}OPENAI_API_KEY\" \"${'$'}OPENAI_CODEX_API_KEY\"\n",
            )
            check(executable.toFile().setExecutable(true)) { "Test executable permission could not be set" }

            val result =
                CodexCliProcessFactory().run(
                    listOf(executable.toString()),
                    timeoutSeconds = 5,
                    environment =
                        mapOf(
                            "OPENAI_API_KEY" to "sentinel-api-key",
                            "OPENAI_CODEX_API_KEY" to "sentinel-codex-key",
                        ),
                )

            assertEquals("|", result.stdout.text)
        }
}
