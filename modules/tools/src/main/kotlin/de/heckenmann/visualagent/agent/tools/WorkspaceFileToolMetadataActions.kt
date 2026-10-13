package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Separates managed-file metadata actions from workspace-file command dispatch. */
internal class WorkspaceFileToolMetadataActions(
    private val workspaceFiles: WorkspaceFileToolPort,
    private val directories: WorkspaceGrantedDirectoryActions,
) {
    fun sync(): ToolResult {
        val report = workspaceFiles.sync()
        return success(
            TOOL_ID,
            buildJsonObject {
                put("added", report.added)
                put("updated", report.updated)
                put("removed", report.removed)
                put("total", report.total)
            }.toString(),
        )
    }

    fun hash(record: ToolWorkspaceFile): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("algorithm", "sha256")
                put("sha256", workspaceFiles.hash(record))
            }.toString(),
        )

    fun mime(input: JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.string("path")
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(TOOL_ID, directories.mimeJson(rootId, requireNotNull(path) { "Missing path" }).toString())
        }
        val record = workspaceFiles.requireFile(input.string("id"), path)
        val detected = workspaceFiles.detectMimeType(record)
        return success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("detectedMimeType", detected.detectedMimeType)
                put("storedMimeType", detected.storedMimeType)
                put("sizeBytes", detected.sizeBytes)
                put("sha256", detected.sha256)
            }.toString(),
        )
    }

    fun extractPdfText(record: ToolWorkspaceFile): ToolResult {
        val text = workspaceFiles.extractPdfText(record)
        return success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("cached", text.cached)
                put("content", text.text)
            }.toString(),
        )
    }

    private companion object {
        const val TOOL_ID = "workspace:file"
    }
}
