package de.heckenmann.visualagent.ui.panels.chat

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.tools.ToolCallEvent

/**
 * Maps provider/tool events into ChatPanel message models.
 */
internal class ChatMessageMapper(
    private val toolHistoryParser: ChatToolHistoryParser,
) {
    /**
     * Maps persisted conversation message to a UI message.
     */
    fun fromHistory(message: Message): ChatMessage? {
        if (message.content.isBlank()) return null
        return ChatMessage(
            role = message.role,
            content = normalizeHistoryContent(message.content),
            isToolEvent = toolHistoryParser.isToolHistoryEntry(message),
            toolData = message.metadata?.let(toolHistoryParser::parseToolMetadata),
        )
    }

    /**
     * Maps a finished tool-call event to a compact UI message.
     */
    fun fromToolEvent(event: ToolCallEvent): ChatMessage {
        val status =
            when {
                event.toolId == "thinking" -> "thinking"
                event.result.success -> "ok"
                else -> "error"
            }
        return ChatMessage(
            role = "assistant",
            content = toolSummary(event, status),
            isToolEvent = true,
            toolData =
                ToolMessageData(
                    toolId = event.toolId,
                    status = status,
                    durationMillis = event.durationMillis,
                    inputJson = event.inputJson,
                    resultContent = event.result.content,
                    resultError = event.result.error,
                ),
        )
    }

    private fun toolSummary(
        event: ToolCallEvent,
        status: String,
    ): String {
        val baseSummary = "Tool ${event.toolId} (${event.durationMillis}ms) · $status"
        val details = event.result.content.trim()
        return when {
            details.isNotBlank() -> "$baseSummary · ${details.lineSequence().firstOrNull().orEmpty().take(120)}"
            !event.result.error.isNullOrBlank() -> "$baseSummary · ${event.result.error}"
            else -> baseSummary
        }
    }

    private fun normalizeHistoryContent(content: String): String =
        when {
            content.startsWith("Recovery note: Could not auto-resume interrupted request") ->
                "I could not resume the previous request automatically. ${recoveryHint(content)}"
            else -> content
        }

    private fun recoveryHint(content: String): String =
        when {
            "api key" in content.lowercase() ->
                "Authentication failed. Check the provider API key and base URL in Session settings."
            "subscription" in content.lowercase() || "upgrade" in content.lowercase() || "403" in content ->
                "The selected model is not available for this account. Choose another model or update the provider subscription."
            else -> "Check the active provider and model, then try again."
        }
}
