package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderTurnResponse
import de.heckenmann.visualagent.agent.text.ResponseRepetitionGuard
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging
import java.time.Instant

/**
 * Handles conversation and history operations for [AgentManager].
 */
internal class AgentManagerConversationOps(
    private val owner: AgentManager,
) {
    private val logger = KotlinLogging.logger {}
    private val contextOps = AgentManagerContextOps(owner)
    private val historyOps = AgentConversationHistoryOps(owner, ::buildMainRequest)

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

    suspend fun sendMessageToAgent(
        agentId: String,
        content: String,
    ): String {
        val agent = owner.subAgentOpsProvider.getSubAgent(agentId) ?: return "Error: Agent not found"
        val messages = listOf(Message("user", content))
        val response = agent.chat(messages, owner.llmProvider, owner.agentToolConfigService.toolsFor(agent))
        return response.message.content
    }

    suspend fun runAgentJob(
        agentId: String,
        content: String,
    ): AgentJobResult {
        val agent = owner.subAgentOpsProvider.getSubAgent(agentId) ?: throw IllegalArgumentException("Agent not found: $agentId")
        return owner.executeSubAgentJob(agent, content)
    }

    suspend fun startAgentJob(
        name: String,
        role: String,
        templateName: String,
        content: String,
    ): AgentJobResult {
        val agent = owner.createAgent(name, role, templateName)
        return owner.executeSubAgentJob(agent, content)
    }

    fun notifyMainAgentOfJobCompletion(
        jobId: String,
        result: Result<AgentJobResult>,
    ) {
        val success = result.isSuccess
        val notification =
            result.fold(
                onSuccess = { completed ->
                    "Sub-agent job $jobId completed by ${completed.agentName} (${completed.agentId}).\n${completed.content}"
                },
                onFailure = { error ->
                    "Sub-agent job $jobId failed: ${error.message ?: error::class.simpleName.orEmpty()}"
                },
            )
        val metadata =
            buildJsonObject {
                put("type", "sub_agent")
                put("jobId", jobId)
                put("success", success)
                val completed = result.getOrNull()
                put("agentId", completed?.agentId ?: "")
                put("agentName", completed?.agentName ?: "")
            }.toString()
        val message = Message(role = "sub_agent", content = notification, metadata = metadata)
        persist(message)
        val agentId = result.getOrNull()?.agentId ?: "main"
        owner.agentStatusCallbackAdapter.notify(agentId, notification)
    }

    suspend fun sendMessage(
        content: String,
        token: CancellationToken? = null,
    ): String {
        val userMessage = Message("user", content)
        persist(userMessage)
        val requestId =
            java.util.UUID
                .randomUUID()
                .toString()
        token?.throwIfCancelled()
        val assistantContent =
            try {
                owner.responseCoordinator.generateAssistantContentWithRepetitionGuard(requestId, token)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                providerFailureMessage(error)
            }
        token?.throwIfCancelled()
        val assistantMessage = Message(role = "assistant", content = assistantContent)
        persist(assistantMessage)
        owner.finishedToolEventsByRequestId.remove(requestId)
        return assistantMessage.content
    }

    suspend fun streamMessage(
        content: String,
        token: CancellationToken? = null,
        onChunk: (String) -> Unit,
        userEntryId: String,
        assistantEntryId: String,
    ): String {
        ConversationStreamRequest(userEntryId, assistantEntryId, content)
        owner.conversationStore.getConversationMessage(assistantEntryId)?.let { existing ->
            require(existing.role == "assistant") { "Conversation retry assistant entry must have role assistant" }
            val userEntry =
                requireNotNull(owner.conversationStore.getConversationMessage(userEntryId)) {
                    "Conversation retry user entry does not exist"
                }
            require(userEntry.role == "user") { "Conversation retry user entry must have role user" }
            require(userEntry.content == content) { "Conversation retry user content does not match" }
            require(userEntry.metadata == conversationTurnMetadata(assistantEntryId)) {
                "Conversation retry entries do not belong to the same turn"
            }
            onChunk(existing.content)
            return existing.content
        }
        val userMessage = Message("user", content, metadata = conversationTurnMetadata(assistantEntryId), id = userEntryId)
        persist(userMessage)
        val requestId = assistantEntryId
        val collected = StringBuilder()
        var providerTurn: ProviderTurnResponse? = null
        var cancelled = false
        var providerFailure: Throwable? = null
        token?.throwIfCancelled()
        try {
            val request =
                buildMainRequest(loadMainAgentContextFromDb(), requestId)
                    .copy(cancellationToken = token)
            owner.llmProvider.stream(request).collect { chunk ->
                token?.throwIfCancelled()
                chunk.providerTurn?.let { providerTurn = ProviderTurnAccumulator.merge(providerTurn, it) }
                val part = chunk.message.content
                if (part.isNotBlank()) {
                    onChunk(appendStreamPart(collected, part))
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
            logger.info { "Main agent request $requestId cancelled by user" }
        } catch (error: Throwable) {
            providerFailure = error
        }
        if (providerFailure != null) {
            val failureMessage = providerFailureMessage(providerFailure)
            persist(Message("assistant", failureMessage, id = assistantEntryId))
            owner.finishedToolEventsByRequestId.remove(requestId)
            return failureMessage
        }
        var assistantText = collected.toString().trim()
        var presentationText = owner.responseCoordinator.normalizeAssistantPresentationContent(assistantText)
        if (cancelled && assistantText.isNotBlank()) {
            assistantText += " (cancelled)"
            presentationText += " (cancelled)"
        }
        if (ResponseRepetitionGuard.isRunawayRepetition(assistantText)) {
            logger.warn { "Repetition guard detected runaway streaming output; retrying once" }
            assistantText = owner.responseCoordinator.retryAfterRepetition()
            presentationText = assistantText
            providerTurn = null
        }
        assistantText = owner.responseCoordinator.normalizeAssistantContent(assistantText)
        if (assistantText == "(No text response. See tool results above.)") {
            val followup = owner.responseCoordinator.completeToolOnlyTurnWithFollowup(requestId)
            assistantText = followup?.let(owner.responseCoordinator::normalizeAssistantContent) ?: assistantText
            presentationText = followup?.let(owner.responseCoordinator::normalizeAssistantPresentationContent) ?: assistantText
            if (followup != null) providerTurn = null
        }
        val assistantMessage =
            Message(
                "assistant",
                presentationText,
                metadata = providerTurn?.let { ResponseTelemetryMetadata.encode(it, true) },
                id = assistantEntryId,
            )
        persist(assistantMessage)
        owner.finishedToolEventsByRequestId.remove(requestId)
        return assistantText
    }

    fun clearHistory() = historyOps.clearHistory()

    private fun conversationTurnMetadata(assistantEntryId: String): String =
        buildJsonObject {
            put("type", "conversation_turn")
            put("assistantEntryId", assistantEntryId)
        }.toString()

    suspend fun addWelcomeMessageAfterReset(): WelcomeResult =
        owner.welcomeMessageComposer.compose(
            persist = ::persist,
        )

    fun getHistory(): List<Message> = historyOps.getHistory()

    fun appendSystemMessage(content: String) {
        val message = Message(role = "system", content = content)
        persist(message)
    }

    private fun providerFailureMessage(error: Throwable): String {
        val userError = ErrorMessageMapper.map(error)
        return "${userError.summary}\n\n${userError.detail}"
    }

    fun recordToolCall(event: ToolCallEvent) = historyOps.recordToolCall(event)

    fun deleteMessageById(id: String) = historyOps.deleteMessageById(id)

    fun updateMessageContentById(
        id: String,
        newContent: String,
    ) = historyOps.updateMessageContentById(id, newContent)

    fun loadOlderHistory(pageSize: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> = historyOps.loadOlderHistory(pageSize)

    fun readOlderHistoryPage(
        offset: Int,
        pageSize: Int,
    ): ConversationHistoryPage = historyOps.readOlderHistoryPage(offset, pageSize)

    fun readLatestHistoryPage(limit: Int): ConversationHistoryPage = historyOps.readLatestHistoryPage(limit)

    fun loadLatestHistory(limit: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> = historyOps.loadLatestHistory(limit)

    /**
     * Clears the in-memory conversation history and reloads the latest page from
     * the database. This is used by the scroll-to-bottom button to guarantee the
     * user lands on the newest persisted message without paging through
     * intermediate chunks.
     *
     * @return the reloaded history list.
     */
    fun refreshHistoryToLatest(limit: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> =
        historyOps.refreshHistoryToLatest(limit)

    fun loadRecentHistoryFromDb(limit: Int = AgentManagerConstants.INITIAL_HISTORY_LOAD_LIMIT): List<Message> =
        historyOps.loadRecentHistoryFromDb(limit)

    /** Loads the database-backed, bounded source records for main-agent context assembly. */
    fun loadMainAgentContextFromDb(
        userTurnLimit: Int = AgentManagerConstants.MAIN_CONTEXT_USER_TURN_LIMIT,
        recordLimit: Int = AgentManagerConstants.MAIN_CONTEXT_RECORD_LIMIT,
    ): List<Message> = historyOps.loadMainAgentContextFromDb(userTurnLimit, recordLimit)

    fun loadConversationFromDb() = historyOps.loadConversationFromDb()

    fun resumeInterruptedConversationIfNeeded() = historyOps.resumeInterruptedConversationIfNeeded()

    fun registerToolEventListener(): AutoCloseable =
        owner.toolEventBus.addListener { event ->
            if (event.phase != ToolCallPhase.FINISHED) return@addListener
            val requestId = event.context["requestId"]?.toString().orEmpty()
            if (requestId.isBlank()) return@addListener
            owner
                .finishedToolEventsByRequestId
                .computeIfAbsent(requestId) { mutableListOf() }
                .add(event)
        }

    fun buildMainRequest(
        history: List<Message>,
        requestId: String? = null,
        token: CancellationToken? = null,
    ): ChatRequestContext = contextOps.buildMainRequest(history, requestId, token)

    fun buildMainSystemContextPrompt(): String = contextOps.buildMainSystemContextPrompt()
}
