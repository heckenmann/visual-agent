package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.DirectoryToolPort
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
    directories: DirectoryToolPort = UnsupportedDirectoryToolPort,
) : VisualAgentTool {
    private val grantedDirectories = WorkspaceGrantedDirectoryActions(directories)
    private val mediaActions = WorkspaceFileToolMediaActions(workspaceFiles)
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
                "listRoots" -> success(TOOL_ID, grantedDirectories.rootsJson().toString())
                "list" -> list(input.string("rootId"), input.string("path").orEmpty())
                "createDirectory" -> createDirectory(input)
                "search" ->
                    search(
                        input.string("rootId"),
                        input.requiredString("query"),
                        input.string("path").orEmpty(),
                        input.string("entryType"),
                        input.string("mimeType"),
                    )
                "glob" -> glob(input.string("rootId"), input.string("path").orEmpty(), input.requiredString("pattern"))
                "grep" -> grep(input.string("rootId"), input.requiredString("query"), input.string("path").orEmpty())
                "writeText" ->
                    writeText(
                        input.string("rootId"),
                        input.requiredString("path"),
                        input.requiredString("content"),
                    )
                "edit" ->
                    edit(
                        input.string("rootId"),
                        input.requiredString("path"),
                        input.requiredString("oldText"),
                        input.requiredString("newText"),
                    )
                "copy" -> transfer(input, move = false)
                "move" -> transfer(input, move = true)
                "info" -> info(file(input))
                "sync" -> sync()
                "delete" -> delete(input)
                "deleteDirectory" -> deleteDirectory(input)
                "hash" -> hash(file(input))
                "readText" -> readText(input)
                "mime" -> mime(input)
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

    private fun list(
        rootId: String?,
        path: String,
    ): ToolResult {
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(TOOL_ID, grantedDirectories.entriesJson(rootId, path).toString())
        }
        if (rootId == WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(TOOL_ID, mediaActions.workspaceEntries(path).toString())
        }
        return listWorkspace()
    }

    private fun listWorkspace(): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put(
                    "files",
                    buildJsonArray {
                        workspaceFiles.list().forEach { add(workspaceFileJson(it)) }
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

    private fun createDirectory(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val rootId = input.string("rootId")
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(
                TOOL_ID,
                buildJsonObject { put("path", grantedDirectories.createDirectory(rootId, input.requiredString("path"))) }.toString(),
            )
        }
        return success(
            TOOL_ID,
            buildJsonObject {
                put("path", workspaceFiles.createDirectory(input.string("parentDirectory").orEmpty(), input.requiredString("name")))
            }.toString(),
        )
    }

    private fun info(record: ToolWorkspaceFile): ToolResult = success(TOOL_ID, workspaceFileJson(record).toString())

    private fun delete(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.string("path")
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            val relativePath = requireNotNull(path) { "Missing path" }
            grantedDirectories.delete(rootId, relativePath, input.boolean("recursive") ?: false)
            return success(
                TOOL_ID,
                buildJsonObject {
                    put("path", relativePath)
                    put("deleted", true)
                }.toString(),
            )
        }
        return deleteWorkspace(file(input))
    }

    private fun deleteWorkspace(record: ToolWorkspaceFile): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("deleted", workspaceFiles.delete(record))
            }.toString(),
        )

    private fun deleteDirectory(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.requiredString("path")
        val recursive = input.boolean("recursive") ?: false
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            grantedDirectories.delete(rootId, path, recursive)
            return success(
                TOOL_ID,
                buildJsonObject {
                    put("path", path)
                    put("deleted", true)
                }.toString(),
            )
        }
        return workspaceFiles.deleteDirectory(path, recursive).let { result ->
            success(
                TOOL_ID,
                buildJsonObject {
                    put("path", result.relativePath)
                    put("recursive", result.recursive)
                    put("deletedFiles", result.deletedFiles)
                    put("deletedMetadata", result.deletedMetadata)
                }.toString(),
            )
        }
    }

    private fun search(
        rootId: String?,
        query: String,
        path: String,
        entryType: String?,
        mimeType: String?,
    ): ToolResult {
        require(entryType == null || entryType in setOf("file", "directory")) {
            "entryType must be file or directory"
        }
        require(entryType != "directory" || mimeType == null) {
            "mimeType cannot be combined with entryType directory"
        }
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            require(entryType == null || entryType == "file") { "entryType directory is not supported for granted-directory search" }
            require(mimeType == null) { "mimeType is not supported for granted-directory search" }
            return success(TOOL_ID, grantedDirectories.searchJson(rootId, query, path).toString())
        }
        val result = workspaceFiles.search(query, mimeType)
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
                            result.matches.forEach { match ->
                                add(
                                    buildJsonObject {
                                        put("entryType", "file")
                                        put("matchType", match.matchType)
                                        put("snippet", match.snippet)
                                        put("file", workspaceFileJson(match.file))
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

    private fun glob(
        rootId: String?,
        path: String,
        pattern: String,
    ): ToolResult {
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(TOOL_ID, grantedDirectories.globJson(rootId, path, pattern).toString())
        }
        return success(
            TOOL_ID,
            buildJsonObject {
                put(
                    "files",
                    buildJsonArray {
                        workspaceFiles.glob(path, pattern).forEach { add(workspaceFileJson(it)) }
                    },
                )
            }.toString(),
        )
    }

    private fun grep(
        rootId: String?,
        query: String,
        path: String,
    ): ToolResult {
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(TOOL_ID, grantedDirectories.searchJson(rootId, query, path).toString())
        }
        return success(
            TOOL_ID,
            buildJsonObject {
                put(
                    "matches",
                    buildJsonArray {
                        workspaceFiles.grep(query, path).forEach { match ->
                            add(
                                buildJsonObject {
                                    put("path", match.path)
                                    put("line", match.line)
                                    put("snippet", match.snippet)
                                },
                            )
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

    private fun readText(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val rootId = input.string("rootId")
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(
                TOOL_ID,
                buildJsonObject {
                    put("path", input.requiredString("path"))
                    put("content", grantedDirectories.readText(rootId, input.requiredString("path")))
                }.toString(),
            )
        }
        return readWorkspaceText(file(input))
    }

    private fun readWorkspaceText(record: ToolWorkspaceFile): ToolResult =
        success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("content", workspaceFiles.readText(record))
            }.toString(),
        )

    private fun mime(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.string("path")
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(TOOL_ID, grantedDirectories.mimeJson(rootId, requireNotNull(path) { "Missing path" }).toString())
        }
        val record = file(input)
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

    private fun writeText(
        rootId: String?,
        path: String,
        content: String,
    ): ToolResult {
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(
                TOOL_ID,
                buildJsonObject { put("path", grantedDirectories.writeText(rootId, path, content)) }.toString(),
            )
        }
        return success(TOOL_ID, workspaceFileJson(workspaceFiles.writeText(path, content)).toString())
    }

    private fun edit(
        rootId: String?,
        path: String,
        oldText: String,
        newText: String,
    ): ToolResult {
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            return success(
                TOOL_ID,
                buildJsonObject { put("path", grantedDirectories.editText(rootId, path, oldText, newText)) }.toString(),
            )
        }
        return success(TOOL_ID, workspaceFileJson(workspaceFiles.editText(path, oldText, newText)).toString())
    }

    private fun transfer(
        input: kotlinx.serialization.json.JsonObject,
        move: Boolean,
    ): ToolResult {
        val sourceRootId = input.requiredString("sourceRootId")
        val sourcePath = input.requiredString("sourcePath")
        val targetRootId = input.requiredString("targetRootId")
        val targetPath = input.requiredString("targetPath")
        val target =
            when {
                sourceRootId == WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID &&
                    targetRootId == WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID -> {
                    if (move) workspaceFiles.move(sourcePath, targetPath) else workspaceFiles.copy(sourcePath, targetPath)
                    targetPath
                }
                sourceRootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID &&
                    targetRootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID -> {
                    if (move) {
                        grantedDirectories.move(sourceRootId, sourcePath, targetRootId, targetPath)
                    } else {
                        grantedDirectories.copy(sourceRootId, sourcePath, targetRootId, targetPath)
                    }
                }
                else -> error("CROSS_ROOT_TRANSFER_UNAVAILABLE: workspace and granted roots require the file-exchange transport")
            }
        return success(
            TOOL_ID,
            buildJsonObject {
                put("sourceRootId", sourceRootId)
                put("sourcePath", sourcePath)
                put("targetRootId", targetRootId)
                put("targetPath", target)
                put("moved", move)
            }.toString(),
        )
    }

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
    ): ToolResult = success(TOOL_ID, workspaceFileJson(workspaceFiles.renderPdfPage(record, page)).toString())

    private fun imageInfo(record: ToolWorkspaceFile): ToolResult = success(TOOL_ID, mediaActions.imageInfo(record).toString())

    private fun imageBytes(record: ToolWorkspaceFile): ToolResult = success(TOOL_ID, mediaActions.imageBytes(record).toString())

    private fun analyzeImage(
        record: ToolWorkspaceFile,
        prompt: String,
    ): ToolResult = success(TOOL_ID, mediaActions.analyzeImage(record, prompt).toString())

    private fun file(input: kotlinx.serialization.json.JsonObject): ToolWorkspaceFile =
        workspaceFiles.requireFile(input.string("id"), input.string("path"))

    private companion object {
        const val TOOL_ID = "workspace:file"
    }
}
