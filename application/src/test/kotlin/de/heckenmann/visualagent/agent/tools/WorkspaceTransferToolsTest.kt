package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDownloadRequest
import de.heckenmann.visualagent.agent.tools.api.ToolMimeType
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    fun `MIME tool returns content-derived metadata for a managed path`() {
        val port = mockk<WorkspaceFileToolPort>()
        every { port.requireFile(null, "downloads/report.png") } returns file
        every { port.detectMimeType(file) } returns ToolMimeType("image/png", "text/html", 42, "content-hash")

        val tool = WorkspaceMimeTypeTool(port)
        val result = tool.execute("""{"path":"downloads/report.png"}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("\"detectedMimeType\":\"image/png\""))
        assertTrue(result.content.contains("\"storedMimeType\":\"text/html\""))
        verify(exactly = 1) { port.detectMimeType(file) }
    }

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
        verify(exactly = 1) {
            port.download(ToolDownloadRequest("https://example.org/report.png", "downloads", "local.png"))
        }
    }
}
