package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.DirectoryToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryEntry
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryGrant
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMatch
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMimeType
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Encodes opaque, explicitly granted directory access for [WorkspaceFileTool]. */
internal class WorkspaceGrantedDirectoryActions(
    private val directories: DirectoryToolPort,
) {
    fun rootsJson() =
        buildJsonObject {
            put(
                "roots",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", WORKSPACE_ROOT_ID)
                            put("displayName", "Visual Agent workspace")
                            put("origin", "SERVER")
                            put("mode", "READ_WRITE")
                            put("available", true)
                        },
                    )
                    directories.listGrants().forEach { add(grantJson(it)) }
                },
            )
        }

    fun entriesJson(
        rootId: String,
        path: String,
    ) = entriesJson(directories.list(rootId, path))

    fun readText(
        rootId: String,
        path: String,
    ): String = directories.readText(rootId, path)

    fun mimeJson(
        rootId: String,
        path: String,
    ) = mimeJson(directories.detectMimeType(rootId, path))

    fun searchJson(
        rootId: String,
        query: String,
        path: String,
    ) = matchesJson(directories.search(rootId, query, path))

    fun globJson(
        rootId: String,
        path: String,
        pattern: String,
    ) = entriesJson(directories.glob(rootId, path, pattern))

    fun writeText(
        rootId: String,
        path: String,
        content: String,
    ): String = directories.writeText(rootId, path, content)

    fun editText(
        rootId: String,
        path: String,
        oldText: String,
        newText: String,
    ): String = directories.editText(rootId, path, oldText, newText)

    fun createDirectory(
        rootId: String,
        path: String,
    ): String = directories.createDirectory(rootId, path)

    fun delete(
        rootId: String,
        path: String,
        recursive: Boolean,
    ) = directories.delete(rootId, path, recursive)

    fun copy(
        sourceRootId: String,
        sourcePath: String,
        targetRootId: String,
        targetPath: String,
    ): String = directories.copy(sourceRootId, sourcePath, targetRootId, targetPath)

    fun move(
        sourceRootId: String,
        sourcePath: String,
        targetRootId: String,
        targetPath: String,
    ): String = directories.move(sourceRootId, sourcePath, targetRootId, targetPath)

    private fun grantJson(grant: ToolDirectoryGrant) =
        buildJsonObject {
            put("id", grant.id)
            put("displayName", grant.displayName)
            put("origin", grant.origin)
            put("mode", grant.mode)
            put("available", grant.available)
        }

    private fun entriesJson(entries: List<ToolDirectoryEntry>) =
        buildJsonObject {
            put(
                "entries",
                buildJsonArray {
                    entries.forEach { entry ->
                        add(
                            buildJsonObject {
                                put("path", entry.path)
                                put("directory", entry.directory)
                                entry.sizeBytes?.let { put("sizeBytes", it) }
                            },
                        )
                    }
                },
            )
        }

    private fun matchesJson(matches: List<ToolDirectoryMatch>) =
        buildJsonObject {
            put(
                "matches",
                buildJsonArray {
                    matches.forEach { match ->
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
        }

    private fun mimeJson(mime: ToolDirectoryMimeType) =
        buildJsonObject {
            put("detectedMimeType", mime.detectedMimeType)
            put("inspectedBytes", mime.inspectedBytes)
        }

    companion object {
        const val WORKSPACE_ROOT_ID = "workspace"
    }
}
