package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.knowledge.ConversationStore
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

/**
 * Tool that loads or searches persisted conversation history for the current session.
 */
@Component
class HistoryTool(
    private val conversationStore: ConversationStore,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("history"),
            name = ToolId("history").toFunctionName(),
            description =
                "Read conversation history. Actions: load older pages or search by keyword. " +
                    "Input: {\"action\":\"load|search\", ...}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val sessionId = context["sessionId"]?.toString().orEmpty().ifBlank { "main" }
        return when (input.string("action") ?: "load") {
            "load" -> loadPage(sessionId, input)
            "search" -> search(sessionId, input)
            else -> failure("history", "Unsupported history action")
        }
    }

    private fun loadPage(
        sessionId: String,
        input: JsonObject,
    ): ToolResult {
        val limit = (input.int("limit") ?: 20).coerceIn(1, 100)
        val offset = (input.int("offset") ?: 0).coerceAtLeast(0)
        val rows = conversationStore.getConversationMessagesPage(sessionId, limit, offset)
        if (rows.isEmpty()) return success("history", "No messages found for load request.")
        val content =
            rows.joinToString("\n") { row ->
                "[${row.createdAt}] ${row.role}: ${row.content}"
            }
        return success("history", content)
    }

    private fun search(
        sessionId: String,
        input: JsonObject,
    ): ToolResult {
        val query = input.requiredString("query")
        val limit = (input.int("limit") ?: 20).coerceIn(1, 100)
        val rows = conversationStore.searchConversationMessages(sessionId, query, limit)
        if (rows.isEmpty()) return success("history", "No messages matched query '$query'.")
        val content =
            rows.joinToString("\n") { row ->
                "[${row.createdAt}] ${row.role}: ${row.content}"
            }
        return success("history", content)
    }
}
