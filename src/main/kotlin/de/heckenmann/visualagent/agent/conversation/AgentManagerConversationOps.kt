package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentJobResult
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.text.ResponseRepetitionGuard
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import kotlinx.coroutines.flow.collect
import mu.KotlinLogging

/**
 * Handles conversation and history operations for [AgentManager].
 */
internal class AgentManagerConversationOps(
    private val owner: AgentManager,
) {
    private val logger = KotlinLogging.logger {}
    private val historyOps = AgentConversationHistoryOps(owner, ::buildMainRequest)

    private fun persist(message: Message): Message {
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
        val notification =
            result.fold(
                onSuccess = { completed ->
                    "Sub-agent job $jobId completed by ${completed.agentName} (${completed.agentId}).\n${completed.content}"
                },
                onFailure = { error ->
                    "Sub-agent job $jobId failed: ${error.message ?: error::class.simpleName.orEmpty()}"
                },
            )
        val message = Message(role = "system", content = notification)
        synchronized(owner.conversationHistory) {
            owner.conversationHistory.add(message)
        }
        persist(message)
        val agentId = result.getOrNull()?.agentId ?: "main"
        AgentManager.notifyAgent(agentId, notification)
    }

    suspend fun sendMessage(content: String): String {
        val userMessage = Message("user", content)
        persist(userMessage)
        val requestId =
            java.util.UUID
                .randomUUID()
                .toString()
        val assistantContent = owner.responseCoordinator.generateAssistantContentWithRepetitionGuard(requestId)
        val assistantMessage = Message(role = "assistant", content = assistantContent)
        persist(assistantMessage)
        owner.finishedToolEventsByRequestId.remove(requestId)
        return assistantMessage.content
    }

    suspend fun streamMessage(
        content: String,
        onChunk: (String) -> Unit,
    ): String {
        val userMessage = Message("user", content)
        persist(userMessage)
        val requestId =
            java.util.UUID
                .randomUUID()
                .toString()
        val collected = StringBuilder()
        owner.llmProvider.stream(buildMainRequest(loadRecentHistoryFromDb(), requestId)).collect { chunk ->
            val part = chunk.message.content
            if (part.isNotBlank()) {
                collected.append(part)
                onChunk(part)
            }
        }
        var assistantText = collected.toString().trim()
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

    suspend fun addWelcomeMessageAfterReset(): String {
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message(
                            role = "system",
                            content =
                                """
                                You are Visual Agent.
                                Greet the user after a conversation reset.
                                Then list in short bullet points what you can do in this app as the main orchestrator:
                                - coordinate worker agents
                                - create, update, delete, and assign sub-agents
                                - review worker results
                                - answer project questions from the available conversation context
                                - keep the task plan moving through worker delegation
                                Keep it concise and friendly.
                                """.trimIndent(),
                        ),
                    ),
                enabledTools = emptySet(),
                metadata = mapOf("sessionId" to AgentManager.MAIN_SESSION_ID, "agent" to "main"),
            )
        val generated =
            owner.llmProvider
                .chat(request)
                .message
                .content
                .trim()
        val welcome = generated.ifBlank { "Hello! I'm ready to help with your project tasks." }
        val message = Message(role = "assistant", content = welcome)
        persist(message)
        return welcome
    }

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
    ): ChatRequestContext {
        val contextPrompt = buildMainSystemContextPrompt()
        val preparedMessages = mutableListOf<Message>()
        preparedMessages += Message("system", contextPrompt)
        val userInstruction =
            de.heckenmann.visualagent.config.AppConfig.instance.userModelInstruction
                .trim()
        if (userInstruction.isNotBlank()) {
            preparedMessages +=
                Message(
                    "system",
                    "User preferences and wishes for this session:\n$userInstruction",
                )
        }
        preparedMessages += history
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
        )
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
        AgentManager.notifyAgent(agent.id, "STATUS:${agent.status.name}")
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
            AgentManager.notifyAgent(agent.id, "STATUS:${agent.status.name}")
        }
    }
}
