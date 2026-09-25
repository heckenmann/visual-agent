package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderTurnResponse
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** Handles conversation orchestration and delegates persistence and streaming details. */
internal class AgentManagerConversationOps(
    private val owner: AgentManager,
) {
    private val contextOps = AgentManagerContextOps(owner)
    private val historyOps = AgentConversationHistoryOps(owner, ::buildMainRequest)
    private val streamingOps by lazy {
        AgentManagerConversationStreamingOps(
            owner = owner,
            loadHistoryContext = { historyOps.loadMainAgentContextFromDb() },
            buildRequest = { history, requestId -> buildMainRequest(history, requestId) },
            persist = ::persist,
            publishCompletion = ::publishAssistantCompletion,
            mapProviderFailure = ::providerFailureMessage,
        )
    }

    /** Persists one conversation message and mirrors its generated timeline metadata in memory. */
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
        if (existingIndex >= 0) owner.conversationHistory[existingIndex] = persisted else owner.conversationHistory.add(persisted)
        return persisted
    }

    /** Sends a single request to a configured sub-agent. */
    suspend fun sendMessageToAgent(
        agentId: String,
        content: String,
    ): String {
        val agent = owner.subAgentOpsProvider.getSubAgent(agentId) ?: return "Error: Agent not found"
        return agent.chat(listOf(Message("user", content)), owner.llmProvider, owner.agentToolConfigService.toolsFor(agent)).message.content
    }

    /** Executes one scheduled sub-agent job. */
    suspend fun runAgentJob(
        agentId: String,
        content: String,
    ): AgentJobResult {
        val agent = owner.subAgentOpsProvider.getSubAgent(agentId) ?: throw IllegalArgumentException("Agent not found: $agentId")
        return owner.executeSubAgentJob(agent, content)
    }

    /** Creates a sub-agent from a template and executes its first job. */
    suspend fun startAgentJob(
        name: String,
        role: String,
        templateName: String,
        content: String,
    ): AgentJobResult = owner.executeSubAgentJob(owner.createAgent(name, role, templateName), content)

    /** Persists a sub-agent completion notification and reports it to the agent status observer. */
    fun notifyMainAgentOfJobCompletion(
        jobId: String,
        result: Result<AgentJobResult>,
    ) {
        val completed = result.getOrNull()
        val notification =
            result.fold(
                onSuccess = { "Sub-agent job $jobId completed by ${it.agentName} (${it.agentId}).\n${it.content}" },
                onFailure = { "Sub-agent job $jobId failed: ${it.message ?: it::class.simpleName.orEmpty()}" },
            )
        val metadata =
            buildJsonObject {
                put("type", "sub_agent")
                put("jobId", jobId)
                put("success", result.isSuccess)
                put("agentId", completed?.agentId ?: "")
                put("agentName", completed?.agentName ?: "")
            }.toString()
        persist(Message(role = "sub_agent", content = notification, metadata = metadata))
        owner.agentStatusCallbackAdapter.notify(completed?.agentId ?: "main", notification)
    }

    /** Sends a non-streaming main-agent turn and persists its visible response. */
    suspend fun sendMessage(
        content: String,
        token: CancellationToken? = null,
    ): String {
        persist(Message("user", content))
        val requestId =
            java.util.UUID
                .randomUUID()
                .toString()
        token?.throwIfCancelled()
        var providerFailed = false
        val assistantContent =
            try {
                owner.responseCoordinator.generateAssistantContentWithRepetitionGuard(requestId, token)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                providerFailed = true
                providerFailureMessage(error)
            }
        token?.throwIfCancelled()
        val assistantMessage = Message(role = "assistant", content = assistantContent)
        val persisted = persist(assistantMessage)
        if (!providerFailed) publishAssistantCompletion(persisted)
        owner.finishedToolEventsByRequestId.remove(requestId)
        return assistantMessage.content
    }

    /** Streams one user turn through the structural assistant-turn coordinator. */
    suspend fun streamMessage(
        content: String,
        token: CancellationToken? = null,
        onChunk: (ConversationStreamUpdate) -> Unit,
        userEntryId: String,
        assistantEntryId: String,
    ): String = streamingOps.streamMessage(content, token, onChunk, userEntryId, assistantEntryId)

    /** Composes and persists the welcome message displayed after a history reset. */
    suspend fun addWelcomeMessageAfterReset(): WelcomeResult = owner.welcomeMessageComposer.compose(persist = ::persist)

    /** Clears both in-memory and persisted conversation history. */
    fun clearHistory() = historyOps.clearHistory()

    /** Publishes completion for an assistant turn with a stable persisted ID. */
    internal fun publishAssistantCompletion(message: Message) {
        val id = message.id ?: return
        owner.conversationCompletionEvents.publish(ConversationCompletionEvent(id, message.timelineSequence))
    }

    /** Returns the in-memory conversation projection. */
    fun getHistory(): List<Message> = historyOps.getHistory()

    /** Persists a system message for application-owned conversation context. */
    fun appendSystemMessage(content: String) = persist(Message(role = "system", content = content))

    private fun providerFailureMessage(error: Throwable): String {
        val userError = ErrorMessageMapper.map(error)
        return "${userError.summary}\n\n${userError.detail}"
    }

    /** Persists one tool event against its correlated assistant-turn parent. */
    fun recordToolCall(event: ToolCallEvent) = historyOps.recordToolCall(event)

    /** Persists a structured assistant provider turn before its tools execute. */
    fun recordProviderAssistantTurn(
        turn: ProviderTurnResponse,
        turnId: String,
        requestId: String,
    ): Message =
        persist(
            Message(
                role = "assistant",
                content = owner.responseCoordinator.normalizeAssistantPresentationContent(turn.content),
                metadata = ResponseTelemetryMetadata.encode(turn, true),
                id = turnId,
                assistantToolTurn = true,
                conversationRequestId = requestId,
            ),
        )

    /** Deletes one message and all tool children owned by it. */
    fun deleteMessageById(id: String) = historyOps.deleteMessageById(id)

    /** Updates the visible content of one persisted message. */
    fun updateMessageContentById(
        id: String,
        newContent: String,
    ) = historyOps.updateMessageContentById(id, newContent)

    /** Loads the next older persisted history page into memory. */
    fun loadOlderHistory(pageSize: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> = historyOps.loadOlderHistory(pageSize)

    /** Reads one older page without changing the loaded-history window. */
    fun readOlderHistoryPage(
        offset: Int,
        pageSize: Int,
    ): ConversationHistoryPage = historyOps.readOlderHistoryPage(offset, pageSize)

    /** Reads the latest page for protocol consumers. */
    fun readLatestHistoryPage(limit: Int): ConversationHistoryPage = historyOps.readLatestHistoryPage(limit)

    /** Appends any missing rows from the latest database page. */
    fun loadLatestHistory(limit: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> = historyOps.loadLatestHistory(limit)

    /** Replaces the in-memory timeline with the latest persisted page. */
    fun refreshHistoryToLatest(limit: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> =
        historyOps.refreshHistoryToLatest(limit)

    /** Reads the recent database-backed history window. */
    fun loadRecentHistoryFromDb(limit: Int = AgentManagerConstants.INITIAL_HISTORY_LOAD_LIMIT): List<Message> =
        historyOps.loadRecentHistoryFromDb(limit)

    /** Loads the full source history used to assemble main-agent context. */
    fun loadMainAgentContextFromDb(
        userTurnLimit: Int = owner.appConfig.contextLength,
        recordLimit: Int = owner.appConfig.contextLength,
    ): List<Message> = historyOps.loadMainAgentContextFromDb(userTurnLimit, recordLimit)

    /** Reloads persisted history and recovers any interrupted request state. */
    fun loadConversationFromDb() = historyOps.loadConversationFromDb()

    /** Schedules recovery for persisted work interrupted by an application restart. */
    fun resumeInterruptedConversationIfNeeded() = historyOps.resumeInterruptedConversationIfNeeded()

    /** Records main-agent tool activity as durable parent-linked history rows. */
    fun registerToolEventListener(): AutoCloseable =
        owner.toolEventBus.addListener { event ->
            if (event.context["agent"] != "main") return@addListener
            val requestId = event.context["requestId"]?.toString().orEmpty()
            if (requestId.isBlank()) return@addListener
            historyOps.recordToolCall(event)
            if (event.phase == ToolCallPhase.FINISHED) {
                owner.finishedToolEventsByRequestId.computeIfAbsent(requestId) { mutableListOf() }.add(event)
            }
        }

    /** Builds the main-agent request from complete history and active provider configuration. */
    fun buildMainRequest(
        history: List<Message>,
        requestId: String? = null,
        token: CancellationToken? = null,
    ): ChatRequestContext = contextOps.buildMainRequest(history, requestId, token)

    /** Builds the provider-neutral system context prompt for the current active model. */
    fun buildMainSystemContextPrompt(): String = contextOps.buildMainSystemContextPrompt()
}
