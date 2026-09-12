package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDownloadRequest
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class WorkspaceTransferToolsTest {
    private val file =
        ToolWorkspaceFile(
            id = "file-1",
            relativePath = "downloads/report.png",
            originalName = "report.png",
            mimeType = "text/html",
            sizeBytes = 42,
            sha256 = "hash",
            hasExtractedText = false,
            importedAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    @Test
    fun `download tool forwards only normalized model request to the server port`() {
        val port = mockk<WorkspaceFileToolPort>()
        every {
            port.download(ToolDownloadRequest("https://example.org/report.png", "downloads", "local.png"))
        } returns file

        val tool = WorkspaceDownloadTool(port)
        val result =
            tool.execute(
                """{"source":"https://example.org/report.png","directory":"downloads","filename":"local.png"}""",
            )

        assertTrue(result.success)
        assertTrue(result.content.contains("downloads/report.png"))
        assertTrue(tool.definition.inputSchema.contains("\"source\""))
        io.mockk.verify(exactly = 1) {
            port.download(ToolDownloadRequest("https://example.org/report.png", "downloads", "local.png"))
        }
    }
}
