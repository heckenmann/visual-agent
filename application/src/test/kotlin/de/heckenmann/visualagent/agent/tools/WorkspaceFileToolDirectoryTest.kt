package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** Verifies directory mutations through the server-owned workspace port. */
class WorkspaceFileToolDirectoryTest {
    @Test
    fun `workspace file tool creates an empty directory through the server port`() {
        val port = mockk<WorkspaceFileToolPort>()
        every { port.createDirectory("projects", "demo") } returns "projects/demo"
        val tool = WorkspaceFileTool(port)

        val result = tool.execute("""{"action":"createDirectory","parentDirectory":"projects","name":"demo"}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("projects/demo"))
    }
}
