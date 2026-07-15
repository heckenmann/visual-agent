package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.text.ResponseRepetitionGuard
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

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
        val persisted = message.copy(id = id)
        owner.conversationHistory.add(persisted)
        return persisted
    }

    suspend fun sendMessageToAgent(
        agentId: String,
        content: String,
    ): String {
        val agent = owner.subAgents[agentId] ?: return "Error: Agent not found"
        val messages = listOf(Message("user", content))
        val response = agent.chat(messages, owner.llmProvider, owner.agentToolConfigService.toolsFor(agent))
        return response.message.content
    }

    suspend fun runAgentJob(
        agentId: String,
        content: String,
    ): AgentJobResult {
        val agent = owner.subAgents[agentId] ?: throw IllegalArgumentException("Agent not found: $agentId")
        return executeAgentJob(agent, content)
    }

    suspend fun startAgentJob(
        name: String,
        role: String,
        templateName: String,
        content: String,
    ): AgentJobResult {
        val agent = owner.createAgent(name, role, templateName)
        return executeAgentJob(agent, content)
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
        val assistantContent = owner.responseCoordinator.generateAssistantContentWithRepetitionGuard(requestId, token)
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
        var cancelled = false
        token?.throwIfCancelled()
        try {
            val request =
                buildMainRequest(loadRecentHistoryFromDb(), requestId)
                    .copy(cancellationToken = token)
            owner.llmProvider.stream(request).collect { chunk ->
                token?.throwIfCancelled()
                val part = chunk.message.content
                if (part.isNotBlank()) {
                    collected.append(part)
                    onChunk(part)
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
            logger.info { "Main agent request $requestId cancelled by user" }
        }
        var assistantText = collected.toString().trim()
        if (cancelled && assistantText.isNotBlank()) {
            assistantText += " (cancelled)"
        }
        if (ResponseRepetitionGuard.isRunawayRepetition(assistantText)) {
            logger.warn { "Repetition guard detected runaway streaming output; retrying once" }
            assistantText = owner.responseCoordinator.retryAfterRepetition()
        }
        assistantText = owner.responseCoordinator.normalizeAssistantContent(assistantText)
        if (assistantText == "(No text response. See tool results above.)") {
            assistantText = owner.responseCoordinator.completeToolOnlyTurnWithFollowup(requestId) ?: assistantText
        }
        val assistantMessage = Message("assistant", assistantText)
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

    fun recordToolCall(event: ToolCallEvent) = historyOps.recordToolCall(event)

    fun deleteMessageById(id: String) = historyOps.deleteMessageById(id)

    fun updateMessageContentById(
        id: String,
        newContent: String,
    ) = historyOps.updateMessageContentById(id, newContent)

    fun loadOlderHistory(pageSize: Int = AgentManager.HISTORY_PAGE_SIZE): List<Message> = historyOps.loadOlderHistory(pageSize)

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
        val userInstruction =
            owner.appConfig.userModelInstruction
                .trim()
        if (userInstruction.isNotBlank()) {
            preparedMessages +=
                Message(
                    "system",
                    "User preferences and wishes for this session:\n$userInstruction",
                )
        }
        preparedMessages += history.map(::normalizeHistoryRoleForProvider)
        val metadata =
            mutableMapOf<String, Any>(
                "sessionId" to AgentManager.MAIN_SESSION_ID,
                "agent" to "main",
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
            else -> message
        }

    fun buildMainSystemContextPrompt(): String {
        val todos = owner.todoStore.listTodos()
        return de.heckenmann.visualagent.agent.context.MainSystemPromptComposer
            .compose(todos, owner.pendingResumeMessage)
    }

    private suspend fun executeAgentJob(
        agent: SubAgent,
        content: String,
    ): AgentJobResult {
        owner.activeJobsByAgentId.compute(agent.id) { _, count -> (count ?: 0) + 1 }
        agent.status = AgentStatus.BUSY
        agent.currentTask = content
        agent.currentTodoId = null
        owner.saveSubAgent(agent)
        owner.agentStatusCallbackAdapter.notify(agent.id, "STATUS:${agent.status.name}")
        return try {
            val response =
                agent.chat(
                    messages = listOf(Message("user", content)),
                    provider = owner.llmProvider,
                    enabledTools = owner.agentToolConfigService.toolsFor(agent),
                )
            AgentJobResult(agent.id, agent.name, response.message.content)
        } finally {
            val remainingJobs =
                owner.activeJobsByAgentId.compute(agent.id) { _, count ->
                    val remaining = (count ?: 1) - 1
                    remaining.takeIf { it > 0 }
                } ?: 0
            agent.status = if (remainingJobs > 0) AgentStatus.BUSY else AgentStatus.IDLE
            if (remainingJobs == 0) {
                agent.currentTask = null
            }
            agent.currentTodoId = null
            owner.saveSubAgent(agent)
            owner.agentStatusCallbackAdapter.notify(agent.id, "STATUS:${agent.status.name}")
        }
    }
}
