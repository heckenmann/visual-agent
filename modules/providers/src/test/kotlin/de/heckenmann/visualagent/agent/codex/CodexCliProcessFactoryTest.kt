package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CodexCliProcessFactoryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `app server process receives fixed arguments and terminates on close`() =
        kotlinx.coroutines.test.runTest {
            val executable = temporaryDirectory.resolve("fake-codex")
            Files.writeString(executable, "#!/bin/sh\nprintf '%s\\n' \"${'$'}1 ${'$'}2\"\nread ignored\n")
            check(executable.toFile().setExecutable(true)) { "Test executable permission could not be set" }
            val child = CodexCliProcessFactory().startAppServer(executable, temporaryDirectory)

            val arguments = withContext(Dispatchers.IO) { child.stdout.bufferedReader().readLine() }
            child.close()

            assertEquals("app-server --stdio", arguments)
            assertFalse(child.isAlive)
        }
}
