package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderTurnResponse
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.text.ResponseRepetitionGuard
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.error.ErrorMessageMapper
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
    private val historyOps = AgentConversationHistoryOps(owner, ::buildMainRequest)

    internal fun persist(message: Message): Message {
        val id =
            owner.conversationStore.saveConversationMessage(
                AgentManager.MAIN_SESSION_ID,
                message.role,
                message.content,
                message.metadata,
            )
        val record = owner.conversationStore.getConversationMessage(id)
        val persisted =
            message.copy(
                id = id,
                createdAtEpochMillis = record?.createdAt?.toEpochMilli() ?: Instant.now().toEpochMilli(),
                timelineSequence = record?.timelineSequence,
            )
        owner.conversationHistory.add(persisted)
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
    ): String {
        val userMessage = Message("user", content)
        persist(userMessage)
        val requestId =
            java.util.UUID
                .randomUUID()
                .toString()
        val collected = StringBuilder()
        var providerTurn: ProviderTurnResponse? = null
        var cancelled = false
        var providerFailure: Throwable? = null
        token?.throwIfCancelled()
        try {
            val request =
                buildMainRequest(loadRecentHistoryFromDb(), requestId)
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
            persist(Message("assistant", failureMessage))
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
                metadata = providerTurn?.let { ResponseTelemetryMetadata.encode(it, owner.appConfig.thinkingEnabled) },
            )
        persist(assistantMessage)
        owner.finishedToolEventsByRequestId.remove(requestId)
        return assistantText
    }

    fun clearHistory() = historyOps.clearHistory()

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

    fun loadOlderHistory(pageSize: Int = AgentManager.HISTORY_PAGE_SIZE): List<Message> = historyOps.loadOlderHistory(pageSize)

    fun readOlderHistoryPage(
        offset: Int,
        pageSize: Int,
    ): ConversationHistoryPage = historyOps.readOlderHistoryPage(offset, pageSize)

    fun readLatestHistoryPage(limit: Int): ConversationHistoryPage = historyOps.readLatestHistoryPage(limit)

    fun loadLatestHistory(limit: Int = AgentManager.HISTORY_PAGE_SIZE): List<Message> = historyOps.loadLatestHistory(limit)

    /**
     * Clears the in-memory conversation history and reloads the latest page from
     * the database. This is used by the scroll-to-bottom button to guarantee the
     * user lands on the newest persisted message without paging through
     * intermediate chunks.
     *
     * @return the reloaded history list.
     */
    fun refreshHistoryToLatest(limit: Int = AgentManager.HISTORY_PAGE_SIZE): List<Message> = historyOps.refreshHistoryToLatest(limit)

    fun loadRecentHistoryFromDb(limit: Int = AgentManager.INITIAL_HISTORY_LOAD_LIMIT): List<Message> =
        historyOps.loadRecentHistoryFromDb(limit)

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
    ): ChatRequestContext {
        val contextPrompt = buildMainSystemContextPrompt()
        val preparedMessages = mutableListOf<Message>()
        preparedMessages += Message("system", contextPrompt)
        preparedMessages += history.map(::normalizeHistoryRoleForProvider)
        val metadata =
            mutableMapOf<String, Any>(
                "sessionId" to AgentManager.MAIN_SESSION_ID,
                "agent" to "main",
                "thinkingEnabled" to owner.appConfig.thinkingEnabled,
            ).apply {
                if (!requestId.isNullOrBlank()) put("requestId", requestId)
            }
        return ChatRequestContext(
            messages = preparedMessages,
            enabledTools = owner.agentToolConfigService.mainAgentTools(),
            metadata = metadata,
            cancellationToken = token,
        )
    }

    /**
     * Map UI-only roles to provider-safe roles.
     *
     * `tool` records are converted to `assistant` so the model sees the
     * result summary, and `sub_agent` notifications become `system`
     * messages.
     *
     * @param message History message with any supported role
     * @return Message with a role the configured LLM provider accepts
     */
    private fun normalizeHistoryRoleForProvider(message: Message): Message =
        when (message.role) {
            "tool" -> message.copy(role = "assistant")
            "sub_agent" -> message.copy(role = "system")
            "assistant" -> message.copy(content = owner.responseCoordinator.removeThinkingMarkup(message.content).trim())
            else -> message
        }

    fun buildMainSystemContextPrompt(): String {
        val todos = owner.todoStore.listTodos()
        return de.heckenmann.visualagent.agent.context.MainSystemPromptComposer
            .compose(todos, owner.pendingResumeMessage, owner.agentToolConfigService, owner.appConfig.userModelInstruction)
    }

}
