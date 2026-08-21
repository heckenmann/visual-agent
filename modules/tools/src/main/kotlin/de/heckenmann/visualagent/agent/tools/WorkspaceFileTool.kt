package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Tool that lets sub-agents inspect, analyze, and manage files imported into the managed workspace.
 *
 * Use cases: UC-0000024, UC-0000025, UC-0000026, UC-0000027.
 */
@AgentTool
class WorkspaceFileTool(
    private val workspaceFiles: WorkspaceFileToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description = workspaceFileToolDescription(),
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
                "createDirectory" -> createDirectory(input.string("parentDirectory").orEmpty(), input.requiredString("name"))
                "search" -> search(input.requiredString("query"), input.string("entryType"), input.string("mimeType"))
                "info" -> info(file(input))
                "sync" -> sync()
                "delete" -> delete(file(input))
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
                        workspaceFiles.list().forEach { add(recordJson(it)) }
                    },
                )
                put(
                    "directories",
                    buildJsonArray {
                        workspaceFiles.listDirectories().forEach { add(JsonPrimitive(it)) }
                    },
                )
            }.toString(),
        )

    private fun createDirectory(
        parentDirectory: String,
        name: String,
    ): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("path", workspaceFiles.createDirectory(parentDirectory, name))
            }.toString(),
        )

    private fun info(record: ToolWorkspaceFile): ToolResult = success(TOOL_ID, recordJson(record).toString())

    private fun delete(record: ToolWorkspaceFile): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("deleted", workspaceFiles.delete(record))
            }.toString(),
        )

    private fun search(
        query: String,
        entryType: String?,
        mimeType: String?,
    ): ToolResult {
        require(entryType == null || entryType in setOf("file", "directory")) {
            "entryType must be file or directory"
        }
        require(entryType != "directory" || mimeType == null) {
            "mimeType cannot be combined with entryType directory"
        }
        val result = workspaceFiles.search(query)
        val directories =
            workspaceFiles
                .listDirectories()
                .filter { it.contains(query, ignoreCase = true) }
        return success(
            TOOL_ID,
            buildJsonObject {
                put("query", result.query)
                entryType?.let { put("entryType", it) }
                mimeType?.let { put("mimeType", it) }
                put(
                    "matches",
                    buildJsonArray {
                        if (entryType != "directory") {
                            result.matches
                                .filter { mimeType == null || it.file.mimeType.equals(mimeType, ignoreCase = true) }
                                .forEach { match ->
                                    add(
                                        buildJsonObject {
                                            put("entryType", "file")
                                            put("matchType", match.matchType)
                                            put("snippet", match.snippet)
                                            put("file", recordJson(match.file))
                                        },
                                    )
                                }
                        }
                        if (entryType != "file") {
                            directories.forEach { directory ->
                                add(
                                    buildJsonObject {
                                        put("entryType", "directory")
                                        put("path", directory)
                                    },
                                )
                            }
                        }
                    },
                )
            }.toString(),
        )
    }

    private fun sync(): ToolResult {
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

    private fun hash(record: ToolWorkspaceFile): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("algorithm", "sha256")
                put("sha256", workspaceFiles.hash(record))
            }.toString(),
        )

    private fun readText(record: ToolWorkspaceFile): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("content", workspaceFiles.readText(record))
            }.toString(),
        )

    private fun extractPdfText(record: ToolWorkspaceFile): ToolResult {
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
        record: ToolWorkspaceFile,
        page: Int,
    ): ToolResult = success(TOOL_ID, recordJson(workspaceFiles.renderPdfPage(record, page)).toString())

    private fun imageInfo(record: ToolWorkspaceFile): ToolResult {
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

    private fun imageBytes(record: ToolWorkspaceFile): ToolResult {
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
        record: ToolWorkspaceFile,
        prompt: String,
    ): ToolResult {
        val response = workspaceFiles.analyzeImage(record, prompt)
        return success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("model", response.model)
                put("content", response.content)
            }.toString(),
        )
    }

    private fun file(input: kotlinx.serialization.json.JsonObject): ToolWorkspaceFile =
        workspaceFiles.requireFile(input.string("id"), input.string("path"))

    private fun recordJson(record: ToolWorkspaceFile) =
        buildJsonObject {
            put("id", record.id)
            put("path", record.relativePath)
            put("originalName", record.originalName)
            put("mimeType", record.mimeType)
            put("sizeBytes", record.sizeBytes)
            put("sha256", record.sha256)
            put("importedAt", record.importedAt.toString())
            put("updatedAt", record.updatedAt.toString())
            put("hasExtractedText", record.hasExtractedText)
        }

    private companion object {
        const val TOOL_ID = "workspace:file"

        /**
         * Returns the tool description for workspace:file with all actions and their parameters.
         */
        fun workspaceFileToolDescription(): String =
            "Manage imported workspace files through the Visual Agent server. This tool remains available even when " +
                "the model runtime's native filesystem sandbox is read-only. For managed workspace files, do not run " +
                "native or terminal permission preflight checks such as `test -w`, and do not abort because they report " +
                "read-only. Use the relevant action below; its result is authoritative. Actions:\n" +
                "- list: {\"action\":\"list\"}. Lists all managed files and directories, including empty directories.\n" +
                "- createDirectory: {\"action\":\"createDirectory\",\"parentDirectory\":\"projects\",\"name\":\"demo\"}. " +
                "Creates an empty managed workspace directory.\n" +
                "- info: {\"action\":\"info\",\"id\":\"...\"} or {\"action\":\"info\",\"path\":\"...\"}. File metadata.\n" +
                "- delete: {\"action\":\"delete\",\"id\":\"...\"} or {\"action\":\"delete\",\"path\":\"...\"}. " +
                "Deletes the managed file and its metadata through the Visual Agent server.\n" +
                "- hash: {\"action\":\"hash\",\"id\":\"...\"}. SHA-256 hash.\n" +
                "- readText: {\"action\":\"readText\",\"id\":\"...\"}. Read text content.\n" +
                "- extractPdfText: {\"action\":\"extractPdfText\",\"id\":\"...\"}. Extract text from PDF.\n" +
                "- renderPdfPage: {\"action\":\"renderPdfPage\",\"id\":\"...\",\"page\":1}. Render PDF page as image.\n" +
                "- imageInfo: {\"action\":\"imageInfo\",\"id\":\"...\"}. Image dimensions and type.\n" +
                "- imageBytes: {\"action\":\"imageBytes\",\"id\":\"...\"}. Base64-encoded image bytes.\n" +
                "- analyzeImage: {\"action\":\"analyzeImage\",\"id\":\"...\",\"prompt\":\"describe this\"}. " +
                "Analyze image with vision model.\n" +
                "For an image in the conversation, use the returned relative path as " +
                "![alt text](workspace:<path>). Do not invent paths or paste imageBytes base64 into a response.\n" +
                "- search: {\"action\":\"search\",\"query\":\"...\",\"entryType\":\"file|directory\",\"mimeType\":\"text/plain\"}. " +
                "Search files and directories; entryType and mimeType are optional.\n" +
                "- sync: {\"action\":\"sync\"}. Sync metadata with filesystem. " +
                "Use id or path to identify files."
    }
}
