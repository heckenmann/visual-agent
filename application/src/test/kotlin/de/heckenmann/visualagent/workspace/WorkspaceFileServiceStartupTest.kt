package de.heckenmann.visualagent.workspace

import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertTrue

class WorkspaceFileServiceStartupTest {
    @Test
    fun `workspace root is created when the file service starts`() {
        val dataRoot = Files.createTempDirectory("workspace-service-startup").resolve("server-data")
        val service = WorkspaceFileService(FakeWorkspaceFileStore(), dataRoot.resolve("visual-agent.db").toString())

        assertTrue(Files.isDirectory(dataRoot.resolve("workspace")))
        assertTrue(Files.isDirectory(service.workspaceRoot()))
    }
}
