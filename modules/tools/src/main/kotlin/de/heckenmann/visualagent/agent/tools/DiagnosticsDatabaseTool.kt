package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import reactor.core.publisher.Mono

/** Reports read-only database connectivity and migration state without exposing stored data. */
@AgentTool
class DiagnosticsDatabaseTool(
    private val diagnostics: ServerDatabaseDiagnosticsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect the Visual Agent server database connection and schema migration status. " +
                    "This read-only diagnostic never queries application records or returns database paths, credentials, or SQL. Input: {}.",
            inputSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        executeReactive(inputJson, context).block()
            ?: failure(TOOL_ID.value, "Database diagnostics are unavailable.")

    override fun executeReactive(
        inputJson: String,
        context: Map<String, Any>,
    ): Mono<ToolResult> {
        val input =
            runCatching { parseObject(inputJson) }.getOrElse {
                return Mono.just(failure(TOOL_ID.value, "Input must be an empty JSON object."))
            }
        if (input.isNotEmpty()) return Mono.just(failure(TOOL_ID.value, "Input must be an empty JSON object."))
        return diagnostics
            .snapshot()
            .map(::result)
            .onErrorReturn(failure(TOOL_ID.value, "Database diagnostics are unavailable."))
    }

    private fun result(snapshot: ServerDatabaseDiagnostic): ToolResult {
        val status =
            when {
                !snapshot.reachable -> "unavailable"
                !snapshot.schemaHistoryAvailable -> "degraded"
                (snapshot.failedMigrationCount ?: 0) > 0 -> "degraded"
                else -> "healthy"
            }
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                put("databaseEngine", "H2")
                put("status", status)
                put("reachable", snapshot.reachable)
                put("schemaHistoryAvailable", snapshot.schemaHistoryAvailable)
                snapshot.currentSchemaVersion?.let { put("currentSchemaVersion", it) }
                snapshot.failedMigrationCount?.let { put("failedMigrationCount", it) }
                snapshot.failureKind?.let { put("failureKind", it) }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("diagnostics:database")
    }
}
