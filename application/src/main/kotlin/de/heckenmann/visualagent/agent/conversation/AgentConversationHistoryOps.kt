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
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

/**
 * Owns persisted conversation history, paging, tool records, and interrupted-run recovery.
 */
internal class AgentConversationHistoryOps(
    private val owner: AgentManager,
    private val buildMainRequest: (List<Message>, String?) -> ChatRequestContext,
) {
    private val mainAgentContextHistoryLoader = MainAgentContextHistoryLoader(owner)

    fun clearHistory() {
        owner.conversationHistory.clear()
        owner.conversationStore.deleteConversationMessages(AgentManagerConstants.MAIN_SESSION_ID)
        owner.loadedHistoryCount = 0
    }

    fun getHistory(): List<Message> = owner.conversationHistory.toList()

    fun deleteMessageById(id: String) {
        owner.conversationStore.deleteConversationMessageById(id)
        owner.conversationHistory.removeAll { it.id == id || it.parentAssistantTurnId == id }
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
        val status = toolCallStatus(event)
        val firstDetailLine =
            event.result.content
                .trim()
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .take(140)
        val compactText =
            when {
                status == "running" -> "Tool ${event.toolId} · running…"
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
        val messageId = stableToolMessageId(event)
        val existing = owner.conversationStore.getConversationMessage(messageId)
        val message =
            Message(
                role = "tool",
                content = compactText,
                metadata = metadata,
                id = messageId,
                contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                parentAssistantTurnId = event.parentAssistantTurnId,
                turnOrder = event.sequence,
            )
        if (existing == null) {
            persist(message)
        } else {
            owner.conversationStore.updateConversationMessage(messageId, compactText, metadata)
            val index = owner.conversationHistory.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                owner.conversationHistory[index] =
                    message.copy(
                        createdAtEpochMillis = existing.createdAt.toEpochMilli(),
                        timelineSequence = existing.timelineSequence,
                    )
            }
        }
    }

    private fun toolCallStatus(event: ToolCallEvent): String {
        if (event.phase == de.heckenmann.visualagent.agent.tools.ToolCallPhase.STARTED) return "running"
        if (event.result.success) return "ok"
        return when (
            event.result.error
                ?.substringBefore(':')
                ?.trim()
                ?.uppercase()
        ) {
            "TOOL_TIMEOUT", "TIMEOUT" -> "timeout"
            "TOOL_CANCELLED", "CANCELLED" -> "cancelled"
            else -> "error"
        }
    }

    fun loadOlderHistory(pageSize: Int): List<Message> {
        val page =
            owner.conversationStore.getConversationHistoryPage(
                sessionId = AgentManagerConstants.MAIN_SESSION_ID,
                limit = pageSize.coerceAtLeast(1),
                offset = owner.loadedHistoryCount,
            )
        val messages = page.records.mapNotNull(::toMessage)
        if (messages.isNotEmpty()) {
            val existingIds = owner.conversationHistory.map { it.id }.toSet()
            val newMessages = messages.filter { it.id !in existingIds }
            if (newMessages.isNotEmpty()) {
                owner.conversationHistory.addAll(0, newMessages)
            }
        }
        owner.loadedHistoryCount = page.nextOffset
        return messages
    }

    fun readOlderHistoryPage(
        offset: Int,
        pageSize: Int,
    ): ConversationHistoryPage {
        val limit = pageSize.coerceAtLeast(1)
        val page = owner.conversationStore.getConversationHistoryPage(AgentManagerConstants.MAIN_SESSION_ID, limit, offset.coerceAtLeast(0))
        return ConversationHistoryPage(page.records.mapNotNull(::toMessage), offset.coerceAtLeast(0), page.hasMore, page.nextOffset)
    }

    fun readLatestHistoryPage(limit: Int): ConversationHistoryPage {
        val pageSize = limit.coerceAtLeast(1)
        val page = owner.conversationStore.getLatestConversationHistoryPage(AgentManagerConstants.MAIN_SESSION_ID, pageSize)
        return ConversationHistoryPage(page.records.mapNotNull(::toMessage), 0, page.hasMore, page.nextOffset)
    }

    /**
     * Loads the newest messages from the database and appends any that are not
     * yet in the in-memory history. Used by the scroll-to-bottom button to jump
     * to the newest persisted message even if a background process wrote new
     * messages after the UI last refreshed.
     */
    fun loadLatestHistory(limit: Int): List<Message> {
        val page = owner.conversationStore.getLatestConversationHistoryPage(AgentManagerConstants.MAIN_SESSION_ID, limit.coerceAtLeast(1))
        val dbMessages = page.records.mapNotNull(::toMessage)
        if (dbMessages.isEmpty()) return emptyList()
        val existingIds = owner.conversationHistory.map { it.id }.toSet()
        val newMessages = dbMessages.filter { it.id !in existingIds }
        if (newMessages.isNotEmpty()) {
            owner.conversationHistory.addAll(newMessages)
        }
        owner.loadedHistoryCount = page.nextOffset
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
        val page = owner.conversationStore.getLatestConversationHistoryPage(AgentManagerConstants.MAIN_SESSION_ID, limit.coerceAtLeast(1))
        val messages = page.records.mapNotNull(::toMessage)
        owner.conversationHistory.addAll(messages)
        owner.loadedHistoryCount = page.nextOffset
        return messages
    }

    fun loadRecentHistoryFromDb(limit: Int): List<Message> =
        owner.conversationStore
            .getLatestConversationHistoryPage(AgentManagerConstants.MAIN_SESSION_ID, limit)
            .records
            .mapNotNull(::toMessage)

    /** Loads the complete eligible history used for main-agent provider requests. */
    fun loadMainAgentContextFromDb(
        userTurnLimit: Int = owner.appConfig.contextLength,
        recordLimit: Int = owner.appConfig.contextLength,
    ): List<Message> = mainAgentContextHistoryLoader.load(userTurnLimit, recordLimit)

    fun loadConversationFromDb() {
        owner.conversationHistory.clear()
        val page =
            owner.conversationStore.getLatestConversationHistoryPage(
                AgentManagerConstants.MAIN_SESSION_ID,
                AgentManagerConstants.INITIAL_HISTORY_LOAD_LIMIT,
            )
        owner.conversationHistory.addAll(page.records.mapNotNull(::toMessage))
        owner.loadedHistoryCount = page.nextOffset
        owner.pendingResumeMessage =
            owner.conversationHistory
                .lastOrNull()
                ?.takeIf { it.role == "user" }
                ?.content
    }

    fun resumeInterruptedConversationIfNeeded() {
        if (owner.pendingResumeMessage == null) return
        owner.scope.launch {
            if (!owner.llmProvider.checkConnectionReactive().awaitSingle()) {
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
                val response = owner.llmProvider.chatReactive(request.copy(messages = messages)).awaitSingle()
                val persisted =
                    persist(Message("assistant", owner.responseCoordinator.normalizeAssistantPresentationContent(response.message.content)))
                owner.conversationOps.publishAssistantCompletion(persisted)
                owner.pendingResumeMessage = null
            }.onFailure { error ->
                val detail = ProviderErrorMessages.userFacing(error)
                persist(Message("assistant", "I could not resume the previous request automatically. $detail"))
                owner.pendingResumeMessage = null
            }
        }
    }

    internal fun persist(message: Message): Message {
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
                message.parentAssistantTurnId,
                message.turnOrder,
                message.assistantToolTurn,
                message.conversationRequestId,
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
        return persisted
    }

    private fun toMessage(row: ConversationRecord): Message? =
        row
            .takeIf { it.role.isNotBlank() && (it.content.isNotBlank() || it.role == "assistant" && it.assistantToolTurn) }
            ?.let {
                Message(
                    role = it.role,
                    content =
                        if (it.role == "assistant") {
                            owner.responseCoordinator.normalizeAssistantPresentationContent(it.content)
                        } else {
                            it.content
                        },
                    metadata = it.metadata?.ifBlank { null },
                    id = it.id,
                    createdAtEpochMillis = it.createdAt.toEpochMilli(),
                    timelineSequence = it.timelineSequence,
                    contextPolicy = it.contextPolicy,
                    parentAssistantTurnId = it.parentAssistantTurnId,
                    turnOrder = it.turnOrder,
                    assistantToolTurn = it.assistantToolTurn,
                    conversationRequestId = it.conversationRequestId,
                )
            }

    private fun stableToolMessageId(event: ToolCallEvent): String {
        val identity =
            listOfNotNull(
                event.requestId,
                event.parentAssistantTurnId,
                event.providerToolCallId,
                event.round?.toString(),
                event.sequence?.toString(),
                event.functionName,
            ).joinToString(":")
        return if (identity.isBlank()) UUID.randomUUID().toString() else UUID.nameUUIDFromBytes(identity.toByteArray()).toString()
    }
}
