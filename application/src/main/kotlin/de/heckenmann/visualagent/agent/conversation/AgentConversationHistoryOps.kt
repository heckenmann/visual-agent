package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.knowledge.ConversationRecord
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Owns persisted conversation history, paging, tool records, and interrupted-run recovery.
 */
internal class AgentConversationHistoryOps(
    private val owner: AgentManager,
    private val buildMainRequest: (List<Message>, String?) -> ChatRequestContext,
) {
    fun clearHistory() {
        owner.conversationHistory.clear()
        owner.conversationStore.deleteConversationMessages(AgentManagerConstants.MAIN_SESSION_ID)
        owner.loadedHistoryCount = 0
    }

    fun getHistory(): List<Message> = owner.conversationHistory.toList()

    fun deleteMessageById(id: String) {
        owner.conversationStore.deleteConversationMessageById(id)
        val index = owner.conversationHistory.indexOfFirst { it.id == id }
        if (index != -1) {
            owner.conversationHistory.removeAt(index)
        }
    }

    fun updateMessageContentById(
        id: String,
        newContent: String,
    ) {
        owner.conversationStore.updateConversationMessageContent(id, newContent)
        val index = owner.conversationHistory.indexOfFirst { it.id == id }
        if (index != -1) {
            val existing = owner.conversationHistory[index]
            owner.conversationHistory[index] = existing.copy(content = newContent)
        }
    }

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
                event.providerToolCallId?.let { put("providerToolCallId", it) }
                event.requestId?.let { put("requestId", it) }
                event.round?.let { put("round", it) }
                event.sequence?.let { put("sequence", it) }
                put("status", status)
                put("durationMillis", event.durationMillis)
                put("inputJson", event.inputJson)
                put("resultContent", event.result.content)
                put("resultError", event.result.error ?: "")
            }.toString()
        persist(
            Message(
                role = "tool",
                content = compactText,
                metadata = metadata,
                contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
            ),
        )
    }

    fun loadOlderHistory(pageSize: Int): List<Message> {
        val rows =
            owner.conversationStore.getConversationMessagesPage(
                sessionId = AgentManagerConstants.MAIN_SESSION_ID,
                limit = pageSize.coerceAtLeast(1),
                offset = owner.loadedHistoryCount,
            )
        val messages = rows.mapNotNull(::toMessage)
        if (messages.isNotEmpty()) {
            val existingIds = owner.conversationHistory.map { it.id }.toSet()
            val newMessages = messages.filter { it.id !in existingIds }
            if (newMessages.isNotEmpty()) {
                owner.conversationHistory.addAll(0, newMessages)
                owner.loadedHistoryCount += newMessages.size
            }
        }
        return messages
    }

    fun readOlderHistoryPage(
        offset: Int,
        pageSize: Int,
    ): ConversationHistoryPage {
        val limit = pageSize.coerceAtLeast(1)
        val messages =
            owner.conversationStore
                .getConversationMessagesPage(AgentManagerConstants.MAIN_SESSION_ID, limit, offset.coerceAtLeast(0))
                .mapNotNull(::toMessage)
        return ConversationHistoryPage(messages, offset.coerceAtLeast(0), messages.size == limit)
    }

    fun readLatestHistoryPage(limit: Int): ConversationHistoryPage {
        val pageSize = limit.coerceAtLeast(1)
        val messages =
            owner.conversationStore
                .getConversationMessages(AgentManagerConstants.MAIN_SESSION_ID, pageSize)
                .mapNotNull(::toMessage)
        return ConversationHistoryPage(messages, 0, messages.size == pageSize)
    }

    /**
     * Loads the newest messages from the database and appends any that are not
     * yet in the in-memory history. Used by the scroll-to-bottom button to jump
     * to the newest persisted message even if a background process wrote new
     * messages after the UI last refreshed.
     */
    fun loadLatestHistory(limit: Int): List<Message> {
        val rows =
            owner.conversationStore.getConversationMessages(
                sessionId = AgentManagerConstants.MAIN_SESSION_ID,
                limit = limit.coerceAtLeast(1),
            )
        val dbMessages = rows.mapNotNull(::toMessage)
        if (dbMessages.isEmpty()) return emptyList()
        val existingIds = owner.conversationHistory.map { it.id }.toSet()
        val newMessages = dbMessages.filter { it.id !in existingIds }
        if (newMessages.isNotEmpty()) {
            owner.conversationHistory.addAll(newMessages)
            owner.loadedHistoryCount += newMessages.size
        }
        return newMessages
    }

    /**
     * Clears the in-memory conversation history and reloads the latest page from
     * the database. Used by the scroll-to-bottom button to guarantee the user
     * lands on the newest persisted message without paging through intermediate
     * chunks.
     */
    fun refreshHistoryToLatest(limit: Int): List<Message> {
        owner.conversationHistory.clear()
        owner.loadedHistoryCount = 0
        val rows =
            owner.conversationStore.getConversationMessages(
                sessionId = AgentManagerConstants.MAIN_SESSION_ID,
                limit = limit.coerceAtLeast(1),
            )
        val messages = rows.mapNotNull(::toMessage)
        owner.conversationHistory.addAll(messages)
        owner.loadedHistoryCount = messages.size
        return messages
    }

    fun loadRecentHistoryFromDb(limit: Int): List<Message> =
        owner.conversationStore
            .getConversationMessages(AgentManagerConstants.MAIN_SESSION_ID, limit)
            .mapNotNull(::toMessage)

    /** Loads the bounded context projection used for main-agent provider requests. */
    fun loadMainAgentContextFromDb(
        userTurnLimit: Int = 10,
        recordLimit: Int = 512,
    ): List<Message> =
        owner.conversationStore
            .getConversationMessagesForContext(AgentManagerConstants.MAIN_SESSION_ID, userTurnLimit, recordLimit)
            .mapNotNull(::toMessage)

    fun loadConversationFromDb() {
        owner.conversationHistory.clear()
        owner.conversationHistory.addAll(loadRecentHistoryFromDb(AgentManagerConstants.INITIAL_HISTORY_LOAD_LIMIT))
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
                val request = buildMainRequest(loadMainAgentContextFromDb(), null)
                val messages = request.messages.toMutableList()
                val systemContextIndex = messages.indexOfFirst { it.role == "system" }
                messages.add(
                    if (systemContextIndex >= 0) systemContextIndex + 1 else 0,
                    Message(
                        "system",
                        "The previous request was interrupted by an app shutdown or failure. Continue the unfinished work from the last user request now.",
                    ),
                )
                val response = owner.llmProvider.chat(request.copy(messages = messages))
                persist(Message("assistant", owner.responseCoordinator.normalizeAssistantPresentationContent(response.message.content)))
                owner.pendingResumeMessage = null
            }.onFailure { error ->
                val detail = ProviderErrorMessages.userFacing(error)
                persist(Message("assistant", "I could not resume the previous request automatically. $detail"))
                owner.pendingResumeMessage = null
            }
        }
    }

    internal fun persist(message: Message) {
        val messageId =
            message.id ?: java.util.UUID
                .randomUUID()
                .toString()
        val id =
            owner.conversationStore.saveConversationMessage(
                messageId,
                AgentManagerConstants.MAIN_SESSION_ID,
                message.role,
                message.content,
                message.metadata,
                message.contextPolicy ?: ConversationContextPolicy.forRole(message.role),
            )
        val record = owner.conversationStore.getConversationMessage(id)
        val persisted =
            message.copy(
                id = id,
                createdAtEpochMillis = record?.createdAt?.toEpochMilli() ?: Instant.now().toEpochMilli(),
                timelineSequence = record?.timelineSequence,
                contextPolicy = record?.contextPolicy ?: message.contextPolicy ?: ConversationContextPolicy.forRole(message.role),
            )
        val existingIndex = owner.conversationHistory.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            owner.conversationHistory[existingIndex] = persisted
        } else {
            owner.conversationHistory.add(persisted)
        }
    }

    private fun toMessage(row: ConversationRecord): Message? =
        row
            .takeIf { it.role.isNotBlank() && it.content.isNotBlank() }
            ?.let {
                Message(
                    role = it.role,
                    content = it.content,
                    metadata = it.metadata?.ifBlank { null },
                    id = it.id,
                    createdAtEpochMillis = it.createdAt.toEpochMilli(),
                    timelineSequence = it.timelineSequence,
                    contextPolicy = it.contextPolicy,
                )
            }
}
