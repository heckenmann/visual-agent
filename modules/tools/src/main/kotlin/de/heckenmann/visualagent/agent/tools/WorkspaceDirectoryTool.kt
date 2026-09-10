package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.DirectoryToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryEntry
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryGrant
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMatch
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Model-facing access to explicitly granted directories through opaque IDs and relative paths. */
@AgentTool
class WorkspaceDirectoryTool(
    private val directories: DirectoryToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "List and access explicitly granted directories. " +
                    "Always use a grantId and grant-relative path; native paths and grant administration are unavailable.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return runCatching {
            when (input.string("action") ?: "listGrants") {
                "listGrants" -> success(TOOL_ID, grantsJson(directories.listGrants()).toString())
                "list" ->
                    success(
                        TOOL_ID,
                        entriesJson(directories.list(input.requiredString("grantId"), input.string("path").orEmpty())).toString(),
                    )
                "readText" ->
                    success(
                        TOOL_ID,
                        buildJsonObject {
                            put("content", directories.readText(input.requiredString("grantId"), input.requiredString("path")))
                        }.toString(),
                    )
                "search" ->
                    success(
                        TOOL_ID,
                        matchesJson(directories.search(input.requiredString("grantId"), input.requiredString("query"))).toString(),
                    )
                "writeText" ->
                    success(
                        TOOL_ID,
                        buildJsonObject {
                            put(
                                "path",
                                directories.writeText(
                                    input.requiredString("grantId"),
                                    input.requiredString("path"),
                                    input.requiredString("content"),
                                ),
                            )
                        }.toString(),
                    )
                else -> failure(TOOL_ID, "Unsupported directory action")
            }
        }.getOrElse { failure(TOOL_ID, it.message ?: "Directory operation failed") }
    }

    private fun grantsJson(grants: List<ToolDirectoryGrant>) =
        buildJsonObject {
            put(
                "grants",
                buildJsonArray {
                    grants.forEach { grant ->
                        add(
                            buildJsonObject {
                                put("id", grant.id)
                                put("displayName", grant.displayName)
                                put("origin", grant.origin)
                                put("mode", grant.mode)
                                put("available", grant.available)
                            },
                        )
                    }
                },
            )
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

    private companion object {
        const val TOOL_ID = "workspace:directory"
    }
}
