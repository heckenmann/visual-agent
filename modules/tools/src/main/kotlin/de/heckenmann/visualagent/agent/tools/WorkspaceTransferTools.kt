package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolDownloadRequest
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolMimeType
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Detects a managed workspace file MIME type from its bytes. */
@AgentTool
class WorkspaceMimeTypeTool(
    private val workspaceFiles: WorkspaceFileToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "Detect the MIME type of a registered workspace file from its content. " +
                    "Input: {\"id\":\"...\"} or {\"path\":\"relative/path\"}. " +
                    "The filename and stored MIME type are not trusted.",
            inputSchema = MIME_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        runCatching {
            val input = parseObject(inputJson)
            val file = workspaceFiles.requireFile(input.string("id"), input.string("path"))
            val detected = workspaceFiles.detectMimeType(file)
            success(TOOL_ID, detected.toJson(file).toString())
        }.getOrElse { error ->
            failure(TOOL_ID, error.message ?: "MIME type detection failed")
        }

    private companion object {
        const val TOOL_ID = "workspace:mime"
        const val MIME_SCHEMA =
            """{"type":"object","properties":{"id":{"type":"string"},"path":{"type":"string"}},"additionalProperties":false,"minProperties":1}"""
    }
}

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

private fun ToolMimeType.toJson(file: ToolWorkspaceFile) =
    buildJsonObject {
        put("id", file.id)
        put("path", file.relativePath)
        put("originalName", file.originalName)
        put("detectedMimeType", detectedMimeType)
        put("storedMimeType", storedMimeType)
        put("sizeBytes", sizeBytes)
        put("sha256", sha256)
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
