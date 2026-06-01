package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.autonomy.AutonomousCoordinator
import de.heckenmann.visualagent.agent.context.MainSystemPromptComposer
import de.heckenmann.visualagent.agent.text.AgentResponseCoordinator
import de.heckenmann.visualagent.agent.text.ResponseRepetitionGuard
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.KnowledgeDb
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents AgentManager.
 */
@Service
class AgentManager(
    internal val knowledgeDb: KnowledgeDb,
    internal val llmProvider: LLMProvider,
    internal val agentToolConfigService: AgentToolConfigService,
    private val toolEventBus: ToolEventBus,
) : DisposableBean {
    /**
     * Immutable todo summary snapshot sourced from the database.
     *
     * @property total Total number of todos
     * @property open Number of pending todos
     * @property inProgress Number of in-progress todos
     * @property completed Number of completed todos
     * @property cancelled Number of cancelled todos
     */
    data class TodoSummary(
        val total: Int,
        val open: Int,
        val inProgress: Int,
        val completed: Int,
        val cancelled: Int,
    )

    companion object {
        internal const val MAIN_SESSION_ID = "main"
        internal const val INITIAL_HISTORY_LOAD_LIMIT = 20
        internal const val HISTORY_PAGE_SIZE = 20
        internal const val REPETITION_GUARD_RETRY_LIMIT = 1
        private var globalAgentCallback: ((String, String) -> Unit)? = null

        /**
         * Executes setAgentCallback.
         */
        fun setAgentCallback(callback: (String, String) -> Unit) {
            globalAgentCallback = callback
        }

        internal fun notifyAgent(
            agentId: String,
            message: String,
        ) {
            globalAgentCallback?.invoke(agentId, message)
        }
    }

    val todoManager: TodoManager =
        TodoManager(
            initialTodos = knowledgeDb.listTodos(),
            onChange = { change -> persistTodoChange(change) },
        )
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val subAgents = mutableMapOf<String, SubAgent>()
    internal val conversationHistory = mutableListOf<Message>()
    internal var pendingResumeMessage: String? = null
    internal var loadedHistoryCount: Int = 0
    internal val finishedToolEventsByRequestId = ConcurrentHashMap<String, MutableList<ToolCallEvent>>()
    private val toolEventListenerHandle: AutoCloseable
    private val autonomousCoordinator: AutonomousCoordinator
    private val responseCoordinator: AgentResponseCoordinator

    init {
        toolEventListenerHandle = registerToolEventListener()
        responseCoordinator =
            AgentResponseCoordinator(
                llmProvider = llmProvider,
                mainSessionId = MAIN_SESSION_ID,
                repetitionGuardRetryLimit = REPETITION_GUARD_RETRY_LIMIT,
                finishedToolEventsByRequestId = finishedToolEventsByRequestId,
                buildMainRequest = ::buildMainRequest,
                buildMainSystemContextPrompt = ::buildMainSystemContextPrompt,
                loadRecentHistoryFromDb = ::loadRecentHistoryFromDb,
            )
        autonomousCoordinator =
            AutonomousCoordinator(
                scope = scope,
                todoManager = todoManager,
                subAgents = subAgents,
                llmProvider = llmProvider,
                knowledgeDb = knowledgeDb,
                agentToolConfigService = agentToolConfigService,
                createAgent = { name, role, templateName -> createAgent(name, role, templateName) },
                saveAgentToDb = ::saveAgentToDb,
                notifyAgent = ::notifyAgent,
            )
        loadAgentsFromDb()
        loadConversationFromDb()
        resumeInterruptedConversationIfNeeded()
    }

    /**
     * Releases tool listeners before bean destruction.
     */
    override fun destroy() {
        runCatching { toolEventListenerHandle.close() }
    }

    /**
     * Load SubAgents from KnowledgeDb. Create default agents if DB is empty.
     */
    private fun loadAgentsFromDb() {
        val agents = knowledgeDb.listAgents()
        println("[AgentManager] Found ${agents.size} agents in DB")
        // Ensure a minimum set of default agents for deterministic behavior in tests and UI
        if (agents.size < 3) {
            createDefaultAgents()
            println("[AgentManager] Created default agents; subAgents size=${subAgents.size}")
            return
        }

        // Sort agents by numeric id when possible to preserve expected ordering (1,2,3...)
        val sortedAgents = agents.sortedBy { it["id"]?.toString()?.toIntOrNull() ?: Int.MAX_VALUE }

        sortedAgents.forEach { agentMap ->
            try {
                val agent = mapAgentRecord(agentMap, resetStatusToIdle = true)
                println("[AgentManager] Loading agent id=${agent.id} status=${agent.status}")
                subAgents[agent.id] = agent
            } catch (e: Exception) {
                println("[AgentManager] Error loading agent: ${e.message}")
            }
        }
        println("[AgentManager] Loaded subAgents keys: ${subAgents.keys} and statuses=${subAgents.mapValues { it.value.status }}")
    }

    internal fun mapAgentRecord(
        agentMap: Map<String, Any>,
        resetStatusToIdle: Boolean,
    ): SubAgent {
        val rawCurrentTask = agentMap["currentTask"] as? String
        val persistedStatus =
            runCatching {
                AgentStatus.valueOf((agentMap["status"] as? String).orEmpty())
            }.getOrDefault(AgentStatus.IDLE)
        return SubAgent(
            id = agentMap["id"] as String,
            name = agentMap["name"] as String,
            role = agentMap["role"] as String,
            status = if (resetStatusToIdle) AgentStatus.IDLE else persistedStatus,
            currentTask = rawCurrentTask?.ifBlank { null },
            parentAgentId = (agentMap["parentAgentId"] as? String)?.ifBlank { null },
            config =
                try {
                    Json.decodeFromString(agentMap["config"] as String)
                } catch (_: Exception) {
                    AgentConfig()
                },
        )
    }

    /**
     * Create default SubAgents if database is empty.
     */
    private fun createDefaultAgents() {
        val defaults =
            listOf(
                SubAgent(
                    "1",
                    "Researcher",
                    "Web research and information gathering",
                    AgentStatus.IDLE,
                    config = AgentConfig.fromTemplate("researcher"),
                ),
                SubAgent("2", "Coder", "Code implementation and review", AgentStatus.IDLE, config = AgentConfig.fromTemplate("coder")),
                SubAgent("3", "Documenter", "Documentation writing", AgentStatus.IDLE, config = AgentConfig.fromTemplate("documenter")),
            )
        defaults.forEach { agent ->
            saveAgentToDb(agent)
            subAgents[agent.id] = agent
        }
        println("[AgentManager] Created ${defaults.size} default agents")
    }

    /**
     * Persist SubAgent to KnowledgeDb.
     */
    internal fun saveAgentToDb(agent: SubAgent) {
        val configJson = Json.encodeToString(agent.config)
        knowledgeDb.saveAgent(
            id = agent.id,
            name = agent.name,
            role = agent.role,
            status = agent.status.name,
            currentTask = agent.currentTask,
            parentAgentId = agent.parentAgentId,
            configJson = configJson,
        )
    }

    // ============ Agent Management API ============

    /**
     * Executes getSubAgents.
     */
    fun getSubAgents(): List<SubAgent> = subAgents.values.toList()

    /**
     * Executes getSubAgent.
     */
    fun getSubAgent(id: String): SubAgent? = subAgents[id]

    /**
     * Loads all todo rows directly from the database.
     *
     * @return Current persisted todos
     */
    fun getTodosFromDb(): List<Todo> = knowledgeDb.listTodos()

    /**
     * Loads a persisted todo summary directly from the database.
     *
     * @return Todo counters derived from persisted state
     */
    fun getTodoSummaryFromDb(): TodoSummary {
        val todos = knowledgeDb.listTodos()
        return TodoSummary(
            total = todos.size,
            open = todos.count { it.status == TodoStatus.PENDING },
            inProgress = todos.count { it.status == TodoStatus.IN_PROGRESS },
            completed = todos.count { it.status == TodoStatus.COMPLETED },
            cancelled = todos.count { it.status == TodoStatus.CANCELLED },
        )
    }

    /**
     * Create a new SubAgent with optional template configuration.
     *
     * @param name Display name for the agent
     * @param role Agent role/description
     * @param templateName Template name (researcher, coder, documenter, reviewer, tester)
     * @return Created SubAgent with generated UUID
     */
    fun createAgent(
        name: String,
        role: String,
        templateName: String = "researcher",
    ): SubAgent {
        val id = UUID.randomUUID().toString().take(8)
        val agent = SubAgent.fromTemplate(id, name, role, templateName)
        saveAgentToDb(agent)
        subAgents[agent.id] = agent
        // Notify listeners about new agent
        notifyAgent(agent.id, "CREATED")
        println("[AgentManager] Created agent: $id ($name)")
        return agent
    }

    /**
     * Update an existing SubAgent's properties and persist.
     */
    fun updateAgent(
        id: String,
        name: String? = null,
        role: String? = null,
        config: AgentConfig? = null,
    ): Boolean {
        val agent = subAgents[id] ?: return false
        if (name != null) agent.name = name
        if (role != null) agent.role = role
        if (config != null) agent.config = config
        agent.updatedAt = System.currentTimeMillis()
        saveAgentToDb(agent)
        println("[AgentManager] Updated agent: $id")
        return true
    }

    /**
     * Delete a SubAgent and remove from database.
     */
    fun deleteAgent(id: String): Boolean {
        val removed = subAgents.remove(id)
        if (removed != null) {
            knowledgeDb.deleteAgent(id)
            println("[AgentManager] Deleted agent: $id")
            return true
        }
        return false
    }

    // ============ Chat & Messaging ============

    /**
     * Executes sendMessageToAgent.
     */
    suspend fun sendMessageToAgent(
        agentId: String,
        content: String,
    ): String {
        val agent = subAgents[agentId] ?: return "Error: Agent not found"
        val messages = listOf(Message("user", content))
        val response = agent.chat(messages, llmProvider, agentToolConfigService.toolsFor(agent))
        return response.message.content
    }

    /**
     * Executes sendMessage.
     */
    suspend fun sendMessage(content: String): String {
        val userMessage = Message("user", content)
        conversationHistory.add(userMessage)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, userMessage.role, userMessage.content, userMessage.metadata)
        val requestId = UUID.randomUUID().toString()
        val assistantContent = responseCoordinator.generateAssistantContentWithRepetitionGuard(requestId)
        val assistantMessage = Message(role = "assistant", content = assistantContent)
        conversationHistory.add(assistantMessage)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, assistantMessage.role, assistantMessage.content, assistantMessage.metadata)
        finishedToolEventsByRequestId.remove(requestId)
        return assistantMessage.content
    }

    /**
     * Executes streamMessage.
     */
    suspend fun streamMessage(
        content: String,
        onChunk: (String) -> Unit,
    ): String {
        val userMessage = Message("user", content)
        conversationHistory.add(userMessage)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, userMessage.role, userMessage.content, userMessage.metadata)
        val requestId = UUID.randomUUID().toString()
        val collected = StringBuilder()
        llmProvider.stream(buildMainRequest(loadRecentHistoryFromDb(), requestId)).collect { chunk ->
            val part = chunk.message.content
            if (part.isNotBlank()) {
                collected.append(part)
                onChunk(part)
            }
        }
        var assistantText = collected.toString().trim()
        if (ResponseRepetitionGuard.isRunawayRepetition(assistantText)) {
            println("[AgentManager] repetition-guard: detected runaway repetition in streaming output, retrying once")
            assistantText = responseCoordinator.retryAfterRepetition()
        }
        assistantText = responseCoordinator.normalizeAssistantContent(assistantText)
        if (assistantText == "(No text response. See tool results above.)") {
            assistantText = responseCoordinator.completeToolOnlyTurnWithFollowup(requestId) ?: assistantText
        }
        val assistantMessage = Message("assistant", assistantText)
        conversationHistory.add(assistantMessage)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, assistantMessage.role, assistantMessage.content, assistantMessage.metadata)
        finishedToolEventsByRequestId.remove(requestId)
        return assistantText
    }

    private fun loadRecentHistoryFromDb(limit: Int = INITIAL_HISTORY_LOAD_LIMIT): List<Message> =
        knowledgeDb.getConversationMessages(MAIN_SESSION_ID, limit).mapNotNull { row ->
            val role = row["role"].orEmpty()
            val content = row["content"].orEmpty()
            if (role.isBlank() || content.isBlank()) {
                null
            } else {
                Message(role = role, content = content, metadata = row["metadata"]?.ifBlank { null })
            }
        }

    /**
     * Registers a listener that groups finished tool events by request id.
     *
     * @return Close handle that removes the listener
     */
    private fun registerToolEventListener(): AutoCloseable =
        toolEventBus.addListener { event ->
            if (event.phase != ToolCallPhase.FINISHED) return@addListener
            val requestId = event.context["requestId"]?.toString().orEmpty()
            if (requestId.isBlank()) return@addListener
            finishedToolEventsByRequestId
                .computeIfAbsent(requestId) { mutableListOf() }
                .add(event)
        }

    /**
     * Executes clearHistory.
     */
    fun clearHistory() {
        conversationHistory.clear()
        knowledgeDb.deleteConversationMessages(MAIN_SESSION_ID)
    }

    /**
     * Requests a model-generated welcome message after reset and persists it in conversation history.
     *
     * @return Welcome message content generated by the model
     */
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
                                Then list in short bullet points what you can do in this app:
                                - answer project questions
                                - read/search files
                                - manage todos
                                - suggest and implement code changes
                                - run terminal commands in the workspace
                                - inspect context/history
                                Keep it concise and friendly.
                                """.trimIndent(),
                        ),
                    ),
                enabledTools = emptySet(),
                metadata = mapOf("sessionId" to MAIN_SESSION_ID, "agent" to "main"),
            )
        val generated =
            llmProvider
                .chat(request)
                .message
                .content
                .trim()
        val welcome = generated.ifBlank { "Hello! I'm ready to help with your project tasks." }
        val message = Message(role = "assistant", content = welcome)
        conversationHistory.add(message)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, message.role, message.content, message.metadata)
        return welcome
    }

    /**
     * Executes getHistory.
     */
    fun getHistory(): List<Message> = conversationHistory.toList()

    /**
     * Persist a finished tool call into the conversation history with compact text and full metadata.
     *
     * @param event Tool call event payload
     */
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
        val message = Message(role = "assistant", content = compactText, metadata = metadata)
        conversationHistory.add(message)
        knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, message.role, message.content, message.metadata)
    }

    /**
     * Load the next older history page and prepend it to in-memory conversation history.
     *
     * @param pageSize Number of older messages to load
     * @return Loaded older messages in chronological order
     */
    fun loadOlderHistory(pageSize: Int = HISTORY_PAGE_SIZE): List<Message> {
        val rows =
            knowledgeDb.getConversationMessagesPage(
                sessionId = MAIN_SESSION_ID,
                limit = pageSize.coerceAtLeast(1),
                offset = loadedHistoryCount,
            )
        val messages =
            rows.mapNotNull { row ->
                val role = row["role"].orEmpty()
                val content = row["content"].orEmpty()
                val metadata = row["metadata"]
                if (role.isNotBlank() && content.isNotBlank()) Message(role = role, content = content, metadata = metadata) else null
            }
        if (messages.isNotEmpty()) {
            conversationHistory.addAll(0, messages)
            loadedHistoryCount += messages.size
        }
        return messages
    }

    /**
     * Loads sub-agents directly from persistent storage.
     * Use this for DB-truth snapshots where runtime-only fields are not required.
     *
     * @return Persisted agent rows mapped to SubAgent models
     */
    fun getSubAgentsFromDb(): List<SubAgent> = listSubAgentsFromDb()

    private fun loadConversationFromDb() {
        conversationHistory.clear()
        val rows = knowledgeDb.getConversationMessages(MAIN_SESSION_ID, INITIAL_HISTORY_LOAD_LIMIT)
        rows.forEach { row ->
            val role = row["role"].orEmpty()
            val content = row["content"].orEmpty()
            val metadata = row["metadata"]
            if (role.isNotBlank() && content.isNotBlank()) {
                conversationHistory.add(Message(role = role, content = content, metadata = metadata))
            }
        }
        loadedHistoryCount = conversationHistory.size
        val last = conversationHistory.lastOrNull()
        pendingResumeMessage = if (last?.role == "user") last.content else null
    }

    // ============ Task Management & Execution ============

    /**
     * Executes assignNextTodo.
     */
    fun assignNextTodo(): Boolean = autonomousCoordinator.assignNextTodo()

    /**
     * Executes assignTodoToAgent.
     */
    fun assignTodoToAgent(
        todoId: String,
        agentId: String,
    ): Boolean = autonomousCoordinator.assignTodoToAgent(todoId, agentId)

    /**
     * Executes assignAllPendingTodos.
     */
    fun assignAllPendingTodos(): Int = autonomousCoordinator.assignAllPendingTodos()

    /**
     * Seed TodoManager with UX improvement tasks for autonomous processing.
     */
    fun seedUxTodos() = autonomousCoordinator.seedUxTodos()

    /**
     * Start autonomous processing: assign todos to idle agents until queue drained.
     * Runs in background scope.
     */
    fun startAutonomousProcessing(seed: Boolean = true) = autonomousCoordinator.startAutonomousProcessing(seed)

    /**
     * Enqueues a goal for autonomous execution and starts the autonomous loop if needed.
     *
     * @param goal Main objective to solve end-to-end
     */
    fun startAutonomousMode(goal: String) = autonomousCoordinator.startAutonomousMode(goal)

    private fun buildMainRequest(
        history: List<Message>,
        requestId: String? = null,
    ): ChatRequestContext {
        val contextPrompt = buildMainSystemContextPrompt()
        val preparedMessages = mutableListOf<Message>()
        preparedMessages += Message("system", contextPrompt)
        val userInstruction = AppConfig.instance.userModelInstruction.trim()
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
                "sessionId" to MAIN_SESSION_ID,
                "agent" to "main",
            ).apply {
                if (!requestId.isNullOrBlank()) put("requestId", requestId)
            }
        return ChatRequestContext(
            messages = preparedMessages,
            enabledTools = agentToolConfigService.mainAgentTools(),
            metadata = metadata,
        )
    }

    private fun buildMainSystemContextPrompt(): String {
        val todos = knowledgeDb.listTodos()
        return MainSystemPromptComposer.compose(todos, pendingResumeMessage)
    }

    private fun resumeInterruptedConversationIfNeeded() {
        val resumeText = pendingResumeMessage ?: return
        scope.launch {
            runCatching {
                val resumeInstruction =
                    Message(
                        "system",
                        "The previous request was interrupted by an app shutdown or failure. Continue the unfinished work from the last user request now.",
                    )
                val request = buildMainRequest(listOf(resumeInstruction) + loadRecentHistoryFromDb())
                val response = llmProvider.chat(request)
                val assistantMessage = Message("assistant", responseCoordinator.normalizeAssistantContent(response.message.content))
                conversationHistory.add(assistantMessage)
                knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, assistantMessage.role, assistantMessage.content)
                pendingResumeMessage = null
            }.onFailure { error ->
                val note = Message("assistant", "Recovery note: Could not auto-resume interrupted request (${error.message}).")
                conversationHistory.add(note)
                knowledgeDb.saveConversationMessage(MAIN_SESSION_ID, note.role, note.content)
            }
        }
    }

    /**
     * Loads sub-agents from persistent storage for DB-first read semantics.
     *
     * @return Current persisted agent rows mapped to SubAgent models
     */
    private fun listSubAgentsFromDb(): List<SubAgent> =
        knowledgeDb
            .listAgents()
            .sortedBy { it["id"]?.toString()?.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { row ->
                runCatching { mapAgentRecord(row, resetStatusToIdle = false) }.getOrNull()
            }

    private fun persistTodoChange(change: TodoChange) {
        when (change.type) {
            TodoChangeType.ADDED, TodoChangeType.UPDATED -> {
                val todo = change.todo ?: return
                knowledgeDb.saveTodo(todo)
            }
            TodoChangeType.REMOVED -> {
                val todoId = change.todoId ?: return
                knowledgeDb.deleteTodo(todoId)
            }
            TodoChangeType.CLEARED -> knowledgeDb.clearTodos()
        }
    }
}
