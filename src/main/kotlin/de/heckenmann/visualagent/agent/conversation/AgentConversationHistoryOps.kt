package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.knowledge.ConversationRecord
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Owns persisted conversation history, paging, tool records, and interrupted-run recovery.
 */
internal class AgentConversationHistoryOps(
    private val owner: AgentManager,
    private val buildMainRequest: (List<Message>, String?) -> ChatRequestContext,
) {
    fun clearHistory() {
        owner.conversationHistory.clear()
        owner.conversationStore.deleteConversationMessages(AgentManager.MAIN_SESSION_ID)
    }

    fun getHistory(): List<Message> = owner.conversationHistory.toList()

    fun recordToolCall(event: ToolCallEvent) {
        val status = if (event.result.success) "ok" else "error"
        val firstDetailLine =
            event.result.content
                .trim()
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .take(140)
        val compactText =
            when {
                firstDetailLine.isNotBlank() -> "Tool ${event.toolId} · $status · $firstDetailLine"
                !event.result.error.isNullOrBlank() -> "Tool ${event.toolId} · $status · ${event.result.error}"
                else -> "Tool ${event.toolId} · $status"
            }
        val metadata =
            buildJsonObject {
                put("type", "tool_call")
                put("toolId", event.toolId)
                put("functionName", event.functionName)
                put("status", status)
                put("durationMillis", event.durationMillis)
                put("inputJson", event.inputJson)
                put("resultContent", event.result.content)
                put("resultError", event.result.error ?: "")
            }.toString()
        persist(Message(role = "assistant", content = compactText, metadata = metadata))
    }

    fun loadOlderHistory(pageSize: Int): List<Message> {
        val rows =
            owner.conversationStore.getConversationMessagesPage(
                sessionId = AgentManager.MAIN_SESSION_ID,
                limit = pageSize.coerceAtLeast(1),
                offset = owner.loadedHistoryCount,
            )
        val messages = rows.mapNotNull(::toMessage)
        if (messages.isNotEmpty()) {
            owner.conversationHistory.addAll(0, messages)
            owner.loadedHistoryCount += messages.size
        }
        return messages
    }

    fun loadRecentHistoryFromDb(limit: Int): List<Message> =
        owner.conversationStore
            .getConversationMessages(AgentManager.MAIN_SESSION_ID, limit)
            .mapNotNull(::toMessage)

    fun loadConversationFromDb() {
        owner.conversationHistory.clear()
        owner.conversationHistory.addAll(loadRecentHistoryFromDb(AgentManager.INITIAL_HISTORY_LOAD_LIMIT))
        owner.loadedHistoryCount = owner.conversationHistory.size
        owner.pendingResumeMessage =
            owner.conversationHistory
                .lastOrNull()
                ?.takeIf { it.role == "user" }
                ?.content
    }

    fun resumeInterruptedConversationIfNeeded() {
        if (owner.pendingResumeMessage == null) return
        owner.scope.launch {
            if (!owner.llmProvider.checkConnection()) {
                persist(
                    Message(
                        "assistant",
                        "I could not resume the previous request automatically. The configured provider is currently unreachable.",
                    ),
                )
                owner.pendingResumeMessage = null
                return@launch
            }
            runCatching {
                val instruction =
                    Message(
                        "system",
                        "The previous request was interrupted by an app shutdown or failure. Continue the unfinished work from the last user request now.",
                    )
                val history = listOf(instruction) + loadRecentHistoryFromDb(AgentManager.INITIAL_HISTORY_LOAD_LIMIT)
                val response = owner.llmProvider.chat(buildMainRequest(history, null))
                persist(Message("assistant", owner.responseCoordinator.normalizeAssistantContent(response.message.content)))
                owner.pendingResumeMessage = null
            }.onFailure { error ->
                val detail = ProviderErrorMessages.userFacing(error)
                persist(Message("assistant", "I could not resume the previous request automatically. $detail"))
                owner.pendingResumeMessage = null
            }
        }
    }

    private fun persist(message: Message) {
        owner.conversationHistory.add(message)
        owner.conversationStore.saveConversationMessage(
            AgentManager.MAIN_SESSION_ID,
            message.role,
            message.content,
            message.metadata,
        )
    }

    private fun toMessage(row: ConversationRecord): Message? =
        row
            .takeIf { it.role.isNotBlank() && it.content.isNotBlank() }
            ?.let { Message(it.role, it.content, it.metadata?.ifBlank { null }) }
}
