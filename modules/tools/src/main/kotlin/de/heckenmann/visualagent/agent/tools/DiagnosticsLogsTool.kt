package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerLogDiagnosticEntry
import de.heckenmann.visualagent.agent.tools.api.ServerLogDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerLogQuery
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import reactor.core.publisher.Mono

/** Searches recent redacted server logs without allowing access to arbitrary files. */
@AgentTool
class DiagnosticsLogsTool(
    private val logs: ServerLogDiagnosticsPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Search a bounded in-memory window of recent Visual Agent server logs. Results are filtered and secret-redacted; " +
                    "this tool cannot read arbitrary log files. Optional filters: level, loggerContains, query, correlationId, limit (1-100).",
            inputSchema =
                """{"type":"object","properties":{"level":{"type":"string","enum":["TRACE","DEBUG","INFO","WARN","ERROR"]},"loggerContains":{"type":"string","maxLength":128},"query":{"type":"string","maxLength":256},"correlationId":{"type":"string","maxLength":128},"limit":{"type":"integer","minimum":1,"maximum":100}},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        executeReactive(inputJson, context).block()
            ?: failure(TOOL_ID.value, "Server log diagnostics are unavailable.")

    override fun executeReactive(
        inputJson: String,
        context: Map<String, Any>,
    ): Mono<ToolResult> {
        if (inputJson.length > MAX_INPUT_LENGTH) return Mono.just(failure(TOOL_ID.value, "Log search input is too large."))
        val input =
            runCatching { Json.parseToJsonElement(inputJson).jsonObject }
                .getOrElse { return Mono.just(failure(TOOL_ID.value, "Log search input must be a JSON object.")) }
        if (input.keys.any { it !in ALLOWED_FIELDS }) {
            return Mono.just(failure(TOOL_ID.value, "Input contains an unsupported log filter."))
        }
        val filter =
            runCatching {
                val level = input.string("level")?.uppercase()
                require(level == null || level in LEVELS) { "invalid-level" }
                val loggerContains = input.string("loggerContains")?.takeIf(String::isNotBlank)
                val query = input.string("query")?.takeIf(String::isNotBlank)
                val correlationId = input.string("correlationId")?.takeIf(String::isNotBlank)
                require(
                    listOf(loggerContains?.length, query?.length, correlationId?.length).none { it != null && it > MAX_FILTER_LENGTH },
                ) {
                    "filters-too-long"
                }
                val limit = input.int("limit") ?: DEFAULT_LIMIT
                require(limit in 1..MAX_RESULTS) { "invalid-limit" }
                ServerLogQuery(level, loggerContains, query, correlationId, limit)
            }.getOrElse {
                val message =
                    when (it.message) {
                        "invalid-level" -> "Log level must be TRACE, DEBUG, INFO, WARN, or ERROR."
                        "filters-too-long" -> "Log filters exceed the maximum length."
                        "invalid-limit" -> "Log result limit must be between 1 and $MAX_RESULTS."
                        else -> "Log filters must use the documented JSON types."
                    }
                return Mono.just(failure(TOOL_ID.value, message))
            }
        return logs
            .search(filter)
            .map(::result)
            .onErrorReturn(failure(TOOL_ID.value, "Server log diagnostics are unavailable."))
    }

    private fun result(entries: List<ServerLogDiagnosticEntry>): ToolResult {
        val data =
            buildJsonObject {
                put("hostScope", "visual-agent-server")
                put("returned", entries.size)
                putJsonArray("entries") {
                    entries.forEach { entry ->
                        add(
                            buildJsonObject {
                                put("sequence", entry.sequence)
                                put("timestampEpochMillis", entry.timestampEpochMillis)
                                put("level", entry.level)
                                put("logger", entry.logger)
                                put("thread", entry.thread)
                                put("message", entry.message)
                                entry.correlationId?.let { put("correlationId", it) }
                            },
                        )
                    }
                }
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private companion object {
        val TOOL_ID = ToolId("diagnostics:logs")
        val ALLOWED_FIELDS = setOf("level", "loggerContains", "query", "correlationId", "limit")
        val LEVELS = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
        const val DEFAULT_LIMIT = 50
        const val MAX_RESULTS = 100
        const val MAX_FILTER_LENGTH = 256
        const val MAX_INPUT_LENGTH = 4_096
    }
}
