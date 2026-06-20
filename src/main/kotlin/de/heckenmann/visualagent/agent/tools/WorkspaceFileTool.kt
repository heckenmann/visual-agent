package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * Tool that lets sub-agents inspect and analyze files imported into the managed workspace.
 */
@Component
class WorkspaceFileTool(
    private val workspaceFiles: WorkspaceFileService,
    private val llmProvider: ObjectProvider<LLMProvider>,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "Inspect imported workspace files. Actions: list, info, hash, readText, extractPdfText, renderPdfPage, " +
                    "imageInfo, imageBytes, analyzeImage, search, sync. Use id or path to identify files.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return runCatching {
            when (input.string("action") ?: "list") {
                "list" -> list()
                "search" -> search(input.requiredString("query"))
                "info" -> info(file(input))
                "sync" -> sync()
                "hash" -> hash(file(input))
                "readText" -> readText(file(input))
                "extractPdfText" -> extractPdfText(file(input))
                "renderPdfPage" -> renderPdfPage(file(input), input.int("page") ?: 1)
                "imageInfo" -> imageInfo(file(input))
                "imageBytes" -> imageBytes(file(input))
                "analyzeImage" -> analyzeImage(file(input), input.requiredString("prompt"))
                else -> failure(TOOL_ID, "Unsupported workspace file action")
            }
        }.getOrElse { error ->
            failure(TOOL_ID, error.message ?: error::class.simpleName.orEmpty())
        }
    }

    private fun list(): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put(
                    "files",
                    buildJsonArray {
                        workspaceFiles.listFiles().forEach { add(recordJson(it)) }
                    },
                )
            }.toString(),
        )

    private fun info(record: WorkspaceFileRecord): ToolResult = success(TOOL_ID, recordJson(record).toString())

    private fun search(query: String): ToolResult {
        val result = workspaceFiles.searchFiles(query)
        return success(
            TOOL_ID,
            buildJsonObject {
                put("query", result.query)
                put(
                    "matches",
                    buildJsonArray {
                        result.matches.forEach { match ->
                            add(
                                buildJsonObject {
                                    put("matchType", match.matchType)
                                    put("snippet", match.snippet)
                                    put("file", recordJson(match.record))
                                },
                            )
                        }
                    },
                )
            }.toString(),
        )
    }

    private fun sync(): ToolResult {
        val report = workspaceFiles.syncMetadataWithFilesystem()
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

    private fun hash(record: WorkspaceFileRecord): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("algorithm", "sha256")
                put("sha256", workspaceFiles.hash(record))
            }.toString(),
        )

    private fun readText(record: WorkspaceFileRecord): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("content", workspaceFiles.readText(record))
            }.toString(),
        )

    private fun extractPdfText(record: WorkspaceFileRecord): ToolResult {
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

    private fun renderPdfPage(
        record: WorkspaceFileRecord,
        page: Int,
    ): ToolResult = success(TOOL_ID, recordJson(workspaceFiles.renderPdfPage(record, page)).toString())

    private fun imageInfo(record: WorkspaceFileRecord): ToolResult {
        val info = workspaceFiles.imageInfo(record)
        return success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("mimeType", info.mimeType)
                put("width", info.width)
                put("height", info.height)
                put("sizeBytes", info.sizeBytes)
                put("sha256", info.sha256)
            }.toString(),
        )
    }

    private fun imageBytes(record: WorkspaceFileRecord): ToolResult {
        val bytes = workspaceFiles.imageBytes(record)
        return success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("mimeType", bytes.mimeType)
                put("base64", bytes.base64)
            }.toString(),
        )
    }

    private fun analyzeImage(
        record: WorkspaceFileRecord,
        prompt: String,
    ): ToolResult {
        val bytes = workspaceFiles.imageBytes(record)
        val response =
            runBlocking {
                llmProvider
                    .getObject()
                    .vision(Base64.getDecoder().decode(bytes.base64), prompt)
            }
        return success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("model", response.model)
                put("content", response.message.content)
            }.toString(),
        )
    }

    private fun file(input: kotlinx.serialization.json.JsonObject): WorkspaceFileRecord =
        workspaceFiles.requireFile(input.string("id"), input.string("path"))

    private fun recordJson(record: WorkspaceFileRecord) =
        buildJsonObject {
            put("id", record.id)
            put("path", record.relativePath)
            put("originalName", record.originalName)
            put("mimeType", record.mimeType)
            put("sizeBytes", record.sizeBytes)
            put("sha256", record.sha256)
            put("importedAt", record.importedAt.toString())
            put("updatedAt", record.updatedAt.toString())
            put("hasExtractedText", record.extractedText != null)
        }

    private companion object {
        const val TOOL_ID = "workspace:file"
    }
}
