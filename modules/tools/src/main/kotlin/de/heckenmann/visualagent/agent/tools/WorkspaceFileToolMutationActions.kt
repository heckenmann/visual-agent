package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Executes workspace-file mutations while retaining the same JSON tool-result contract. */
internal class WorkspaceFileToolMutationActions(
    private val workspaceFiles: WorkspaceFileToolPort,
    private val directories: WorkspaceGrantedDirectoryActions,
) {
    fun createDirectory(input: JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path =
            if (rootId != null &&
                rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID
            ) {
                directories.createDirectory(rootId, input.requiredString("path"))
            } else {
                workspaceFiles.createDirectory(input.string("parentDirectory").orEmpty(), input.requiredString("name"))
            }
        return success(TOOL_ID, buildJsonObject { put("path", path) }.toString())
    }

    fun delete(input: JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.string("path")
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            val relativePath = requireNotNull(path) { "Missing path" }
            directories.delete(rootId, relativePath, input.boolean("recursive") ?: false)
            return success(
                TOOL_ID,
                buildJsonObject {
                    put("path", relativePath)
                    put("deleted", true)
                }.toString(),
            )
        }
        val record = workspaceFiles.requireFile(input.string("id"), path)
        return success(
            TOOL_ID,
            buildJsonObject {
                put("id", record.id)
                put("path", record.relativePath)
                put("deleted", workspaceFiles.delete(record))
            }.toString(),
        )
    }

    fun deleteDirectory(input: JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.requiredString("path")
        val recursive = input.boolean("recursive") ?: false
        if (rootId != null && rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID) {
            directories.delete(rootId, path, recursive)
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

    fun writeText(input: JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.requiredString("path")
        val content = input.requiredString("content")
        return if (rootId != null &&
            rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID
        ) {
            success(TOOL_ID, buildJsonObject { put("path", directories.writeText(rootId, path, content)) }.toString())
        } else {
            success(TOOL_ID, workspaceFileJson(workspaceFiles.writeText(path, content)).toString())
        }
    }

    fun edit(input: JsonObject): ToolResult {
        val rootId = input.string("rootId")
        val path = input.requiredString("path")
        val oldText = input.requiredString("oldText")
        val newText = input.requiredString("newText")
        return if (rootId != null &&
            rootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID
        ) {
            success(TOOL_ID, buildJsonObject { put("path", directories.editText(rootId, path, oldText, newText)) }.toString())
        } else {
            success(TOOL_ID, workspaceFileJson(workspaceFiles.editText(path, oldText, newText)).toString())
        }
    }

    fun transfer(
        input: JsonObject,
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
                    targetRootId != WorkspaceGrantedDirectoryActions.WORKSPACE_ROOT_ID ->
                    if (move) {
                        directories.move(
                            sourceRootId,
                            sourcePath,
                            targetRootId,
                            targetPath,
                        )
                    } else {
                        directories.copy(sourceRootId, sourcePath, targetRootId, targetPath)
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

    private companion object {
        const val TOOL_ID = "workspace:file"
    }
}
