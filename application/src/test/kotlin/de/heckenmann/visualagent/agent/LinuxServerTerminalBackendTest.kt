package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.TerminalHost
import de.heckenmann.visualagent.agent.tools.TerminalSandboxRequest
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.workspace.ServerTerminalDirectory
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "VISUAL_AGENT_TEST_BUBBLEWRAP", matches = "true")
class LinuxServerTerminalBackendTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `sandbox reads its grant and rejects writes and ungranted paths`() {
        val grant = Files.createDirectory(temporaryDirectory.resolve("grant")).toRealPath()
        Files.writeString(grant.resolve("allowed"), "allowed-text")
        val outside = Files.writeString(temporaryDirectory.resolve("outside"), "outside-text")
        val invocation =
            TerminalSandboxRequest(
                TerminalHost.SERVER,
                """cat allowed && ! touch denied && ! cat ../outside && ! cat "$outside" """,
                null,
                null,
            )
        val directory = ServerTerminalDirectory(grant, grant, DirectoryAccessMode.READ_ONLY)
        val backend = LinuxServerTerminalBackend()
        assertTrue(backend.supportsPlatform())
        val execution = assertNotNull(backend.start(invocation, directory, emptyMap()))
        try {
            val output = execution.output.bufferedReader().use { it.readText() }
            assertEquals(0, execution.awaitExit(), output)
            assertContains(output, "allowed-text")
            assertFalse(output.contains("outside-text"))
            assertFalse(Files.exists(grant.resolve("denied")))
        } finally {
            execution.cancel()
        }
    }

    @Test
    fun `sandbox refuses a directory replaced after it was authorized`() {
        val grant = Files.createDirectory(temporaryDirectory.resolve("grant")).toRealPath()
        val directory = ServerTerminalDirectory(grant, grant, DirectoryAccessMode.READ_ONLY)
        Files.move(grant, temporaryDirectory.resolve("original"))
        Files.createDirectory(grant)
        val request = TerminalSandboxRequest(TerminalHost.SERVER, ":", null, null)
        val execution = assertNotNull(LinuxServerTerminalBackend().start(request, directory, emptyMap()))
        try {
            val output = execution.output.bufferedReader().use { it.readText() }
            assertEquals(1, execution.awaitExit(), output)
            assertContains(output, "The authorized terminal directory changed")
        } finally {
            execution.cancel()
        }
    }
}
