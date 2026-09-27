package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Exposes explicitly requested values from the Visual Agent server process environment. */
@AgentTool
class SystemEnvironmentTool(
    private val environment: EnvironmentVariablesProvider,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID.toFunctionName(),
            description =
                "Inspect environment variables of the Visual Agent server process. Values, including secrets, are returned " +
                    "unchanged; this tool is globally disabled until explicitly enabled by the user. " +
                    "Use list/search with offset, pageSize, and valueLimit, or get with name, valueOffset, and valueLimit. " +
                    "Input: {\"action\":\"list|search|get\",\"query\":\"PATH\",\"name\":\"OPENAI_API_KEY\"," +
                    "\"offset\":0,\"pageSize\":10,\"valueOffset\":0,\"valueLimit\":1024}.",
            inputSchema =
                """{"type":"object","properties":{"action":{"type":"string","enum":["list","search","get"]},"query":{"type":"string","minLength":1,"maxLength":128},"name":{"type":"string","minLength":1,"maxLength":256},"offset":{"type":"integer","minimum":0},"pageSize":{"type":"integer","minimum":1,"maximum":10},"valueOffset":{"type":"integer","minimum":0},"valueLimit":{"type":"integer","minimum":1,"maximum":4096}},"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        if (inputJson.length > MAX_INPUT_LENGTH) return failure(TOOL_ID.value, "Environment query input is too large.")
        val input = parseObject(inputJson)
        val action = input.string("action")?.lowercase() ?: LIST_ACTION
        if (action !in ACTIONS) return failure(TOOL_ID.value, "Invalid action. Use list, search, or get.")
        if (action == LIST_ACTION && input["query"] != null) return failure(TOOL_ID.value, "list does not accept query.")
        if (action == GET_ACTION && (input["query"] != null || input["offset"] != null || input["pageSize"] != null)) {
            return failure(TOOL_ID.value, "get accepts name, valueOffset, and valueLimit only.")
        }
        if (action != GET_ACTION && (input["name"] != null || input["valueOffset"] != null)) {
            return failure(TOOL_ID.value, "list and search accept query, offset, pageSize, and valueLimit only.")
        }
        val numericFields = listOf("offset", "pageSize", "valueOffset", "valueLimit")
        if (numericFields.any { field -> input[field] != null && input.int(field) == null }) {
            return failure(TOOL_ID.value, "Pagination and value-limit fields must be integers.")
        }
        if (action == GET_ACTION && input.string("name").isNullOrBlank()) {
            return failure(TOOL_ID.value, "get requires a non-empty name.")
        }
        if (action == SEARCH_ACTION && input.string("query").isNullOrBlank()) {
            return failure(TOOL_ID.value, "search requires a non-empty query.")
        }
        if (input.string("query")?.length?.let { it > MAX_QUERY_LENGTH } == true) {
            return failure(TOOL_ID.value, "Search query is too long.")
        }
        if (input.string("name")?.length?.let { it > MAX_NAME_LENGTH } == true) {
            return failure(TOOL_ID.value, "Environment variable name is too long.")
        }
        val valueLimit = input.int("valueLimit") ?: DEFAULT_VALUE_LIMIT
        if (valueLimit !in MIN_VALUE_LIMIT..MAX_VALUE_LIMIT) {
            return failure(TOOL_ID.value, "valueLimit must be between 1 and $MAX_VALUE_LIMIT.")
        }
        val pageSize = input.int("pageSize") ?: DEFAULT_PAGE_SIZE
        if (action != GET_ACTION && pageSize !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            return failure(TOOL_ID.value, "pageSize must be between 1 and $MAX_PAGE_SIZE.")
        }
        val offset = input.int(if (action == GET_ACTION) "valueOffset" else "offset") ?: 0
        if (offset < 0) return failure(TOOL_ID.value, "Offsets must be non-negative.")
        val values = runCatching(environment::snapshot).getOrElse { return failure(TOOL_ID.value, "Server environment is unavailable.") }
        return when (action) {
            GET_ACTION -> get(input, values, valueLimit)
            LIST_ACTION, SEARCH_ACTION -> list(input, values, action, valueLimit)
            else -> failure(TOOL_ID.value, "Invalid action.")
        }
    }

    private fun get(
        input: kotlinx.serialization.json.JsonObject,
        values: Map<String, String>,
        valueLimit: Int,
    ): ToolResult {
        val name =
            input.string("name")?.takeIf(String::isNotBlank)
                ?: return failure(TOOL_ID.value, "get requires a non-empty name.")
        val value = values[name] ?: return failure(TOOL_ID.value, "Environment variable was not found.")
        val offset = input.int("valueOffset") ?: 0
        val totalCodePoints = value.codePointCount(0, value.length)
        if (offset < 0 || offset > totalCodePoints) return failure(TOOL_ID.value, "valueOffset is outside the variable value.")
        val nextOffset = offset + valueLimit.coerceAtMost(totalCodePoints - offset)
        val start = value.offsetByCodePoints(0, offset)
        val end = value.offsetByCodePoints(0, nextOffset)
        val data = environmentValue(name, value, start, end, offset, nextOffset)
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun list(
        input: kotlinx.serialization.json.JsonObject,
        values: Map<String, String>,
        action: String,
        valueLimit: Int,
    ): ToolResult {
        val query =
            if (action == SEARCH_ACTION) {
                input.string("query")?.takeIf(String::isNotBlank)
                    ?: return failure(TOOL_ID.value, "search requires a non-empty query.")
            } else {
                null
            }
        val offset = input.int("offset") ?: 0
        if (offset < 0) return failure(TOOL_ID.value, "offset must be non-negative.")
        val pageSize = input.int("pageSize") ?: DEFAULT_PAGE_SIZE
        if (pageSize !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            return failure(TOOL_ID.value, "pageSize must be between 1 and $MAX_PAGE_SIZE.")
        }
        val matching = values.entries.sortedBy { it.key.lowercase() }.filter { query == null || it.key.contains(query, ignoreCase = true) }
        val page = matching.drop(offset).take(pageSize)
        val data =
            buildJsonObject {
                put("runtimeScope", "visual-agent-server-process")
                put("action", action)
                put("offset", offset)
                put("totalVariables", matching.size)
                put("hasMore", offset.toLong() + page.size < matching.size)
                put("valuesIncluded", true)
                put("redacted", false)
                put("valueLimit", valueLimit)
                put(
                    "variables",
                    buildJsonArray {
                        page.forEach { (name, value) ->
                            val totalCodePoints = value.codePointCount(0, value.length)
                            val endOffset = valueLimit.coerceAtMost(totalCodePoints)
                            val end = value.offsetByCodePoints(0, endOffset)
                            add(environmentValue(name, value, 0, end, 0, endOffset))
                        }
                    },
                )
            }
        return ToolResult(TOOL_ID.value, true, data.toString(), data = data)
    }

    private fun environmentValue(
        name: String,
        value: String,
        start: Int,
        end: Int,
        valueOffset: Int,
        nextValueOffset: Int,
    ) = buildJsonObject {
        val totalCodePoints = value.codePointCount(0, value.length)
        put("name", name)
        put("value", value.substring(start, end))
        put("valueLength", totalCodePoints)
        put("valueOffset", valueOffset)
        put("nextValueOffset", nextValueOffset)
        put("hasMoreValue", nextValueOffset < totalCodePoints)
        put("redacted", false)
    }

    private companion object {
        val TOOL_ID = ToolId("system:env")
        val ACTIONS = setOf("list", "search", "get")
        const val LIST_ACTION = "list"
        const val SEARCH_ACTION = "search"
        const val GET_ACTION = "get"
        const val DEFAULT_PAGE_SIZE = 10
        const val MAX_PAGE_SIZE = 10
        const val MIN_PAGE_SIZE = 1
        const val DEFAULT_VALUE_LIMIT = 1024
        const val MAX_VALUE_LIMIT = 4096
        const val MIN_VALUE_LIMIT = 1
        const val MAX_QUERY_LENGTH = 128
        const val MAX_NAME_LENGTH = 256
        const val MAX_INPUT_LENGTH = 4_096
    }
}
