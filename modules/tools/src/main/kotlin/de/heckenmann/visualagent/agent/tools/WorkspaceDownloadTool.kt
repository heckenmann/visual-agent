package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolDownloadRequest
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Downloads a remote file into the server-owned managed workspace. */
@AgentTool
class WorkspaceDownloadTool(
    private val workspaceFiles: WorkspaceFileToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "Download one HTTP(S), FTP, SFTP, or SCP resource into the managed workspace. " +
                    "Input: {\"source\":\"https://example.org/file.pdf\", " +
                    "\"directory\":\"downloads\",\"filename\":\"optional-name.pdf\"}. " +
                    "The default directory is downloads. Credentials, local paths, redirects, and " +
                    "unsupported protocols are rejected; never include secrets in the source.",
            inputSchema = DOWNLOAD_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        runCatching {
            val input = parseObject(inputJson)
            val file =
                workspaceFiles.download(
                    ToolDownloadRequest(
                        source = input.requiredString("source"),
                        directory = input.string("directory"),
                        filename = input.string("filename"),
                    ),
                )
            success(TOOL_ID, file.toJson().toString())
        }.getOrElse { error ->
            failure(TOOL_ID, error.message ?: "Workspace download failed")
        }

    private companion object {
        const val TOOL_ID = "workspace:download"
        const val DOWNLOAD_SCHEMA =
            """{"type":"object","properties":{"source":{"type":"string"},"directory":{"type":"string"},"filename":{"type":"string"}},"required":["source"],"additionalProperties":false}"""
    }
}

private fun ToolWorkspaceFile.toJson() =
    buildJsonObject {
        put("id", id)
        put("path", relativePath)
        put("originalName", originalName)
        put("mimeType", mimeType)
        put("sizeBytes", sizeBytes)
        put("sha256", sha256)
        put("importedAt", importedAt.toString())
        put("updatedAt", updatedAt.toString())
    }
