package de.heckenmann.visualagent.agent

// TODO(size): 305 effective LOC, needs splitting

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps
import de.heckenmann.visualagent.agent.conversation.WelcomeMessageComposer
import de.heckenmann.visualagent.agent.text.AgentResponseCoordinator
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.PersistenceStores
import de.heckenmann.visualagent.knowledge.SubAgentStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.orchestration.AutonomousCoordinator
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Main orchestration facade for chat, history, todos, and sub-agent coordination.
 *
 * Use cases: UC-0000002, UC-0000003, UC-0000005, UC-0000006, UC-0000014, UC-0000015,
 * UC-0000016, UC-0000017, UC-0000018, UC-0000040.
 */
@Service
class AgentManager
    @Autowired
    constructor(
        internal val conversationStore: ConversationStore,
        internal val todoStore: TodoStore,
        internal val subAgentStore: SubAgentStore,
        internal val memoryStore: MemoryStore,
        internal val llmProvider: LLMProvider,
        internal val agentToolConfigService: AgentToolConfigService,
        internal val toolEventBus: ToolEventBus,
        internal val todoEventBus: TodoEventBus,
        internal val appConfig: AppConfigBean,
        internal val scope: CoroutineScope,
        internal val parallelismProvider: ParallelismProvider,
        internal val agentStatusCallbackAdapter: AgentStatusCallbackAdapter,
    ) : DisposableBean {
        internal constructor(
            stores: PersistenceStores,
            llmProvider: LLMProvider,
            agentToolConfigService: AgentToolConfigService,
            toolEventBus: ToolEventBus,
            todoEventBus: TodoEventBus,
            appConfig: AppConfigBean,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            parallelismProvider: ParallelismProvider = ParallelismProvider(appConfig),
            agentStatusCallbackAdapter: AgentStatusCallbackAdapter = AgentStatusCallbackAdapter(),
        ) : this(
            stores,
            stores,
            stores,
            stores,
            llmProvider,
            agentToolConfigService,
            toolEventBus,
            todoEventBus,
            appConfig,
            scope,
            parallelismProvider,
            agentStatusCallbackAdapter,
        )

        companion object {
            internal const val MAIN_SESSION_ID = "main"
            internal const val INITIAL_HISTORY_LOAD_LIMIT = 20
            internal const val HISTORY_PAGE_SIZE = 20
            internal const val REPETITION_GUARD_RETRY_LIMIT = 1
        }

        internal lateinit var autonomousCoordinator: AutonomousCoordinator
        internal lateinit var responseCoordinator: AgentResponseCoordinator
        internal var todoManager: TodoManager = TodoManager(todoStore, todoEventBus)
        internal val welcomeMessageComposer = WelcomeMessageComposer(llmProvider, appConfig)
        internal val subAgentJobScheduler =
            SubAgentJobScheduler(scope, parallelismProvider)
        internal val conversationOpsProvider = ConversationOpsProvider(toolEventBus)
        internal val subAgentOpsProvider = SubAgentOpsProvider()
        internal val subAgents: Map<String, SubAgent>
            get() = subAgentOpsProvider.allSubAgents
        internal val activeJobsByAgentId = ConcurrentHashMap<String, Int>()
        internal val conversationHistory = mutableListOf<Message>()
        internal var pendingResumeMessage: String? = null
        internal var loadedHistoryCount: Int = 0
        internal val finishedToolEventsByRequestId = ConcurrentHashMap<String, MutableList<ToolCallEvent>>()
        private var toolEventListenerHandle: AutoCloseable? = null
        private val lifecycleOps = AgentManagerLifecycleOps(this)
        internal val conversationOps = AgentManagerConversationOps(this)
        private val autonomyOps = AgentManagerAutonomyOps(this)

        init {
            lifecycleOps.loadAgentsFromDb()
            todoManager.loadInitialTodos()
            todoManager.addListener { change -> lifecycleOps.persistTodoChange(change) }
            conversationOpsProvider.setBuildMainRequest(conversationOps::buildMainRequest)
            conversationOpsProvider.setBuildMainSystemContextPrompt(conversationOps::buildMainSystemContextPrompt)
            conversationOpsProvider.setLoadRecentHistoryFromDb(conversationOps::loadRecentHistoryFromDb)
            conversationOpsProvider.setPersistMessage(conversationOps::persist)
            subAgentOpsProvider.setSaveSubAgent(lifecycleOps::saveAgentToDb)
            subAgentOpsProvider.setCreateAgent { name, role, templateName -> lifecycleOps.createAgent(name, role, templateName) }
            subAgentOpsProvider.setNotifyAgent(agentStatusCallbackAdapter::notify)
            responseCoordinator =
                AgentResponseCoordinator(llmProvider, conversationOpsProvider)
            autonomousCoordinator =
                AutonomousCoordinator(
                    scope = scope,
                    todoManager = todoManager,
                    llmProvider = llmProvider,
                    todoStore = todoStore,
                    memoryStore = memoryStore,
                    agentToolConfigService = agentToolConfigService,
                    jobScheduler = subAgentJobScheduler,
                    parallelismProvider = parallelismProvider,
                    todoEventBus = todoEventBus,
                    conversationOps = conversationOpsProvider,
                    subAgentOps = subAgentOpsProvider,
                )
            todoEventBus.addListener { change ->
                val todo = change.todo ?: return@addListener
                if (change.type != TodoChangeType.UPDATED) return@addListener
                when (todo.status) {
                    TodoStatus.COMPLETED, TodoStatus.CANCELLED -> triggerMainAgentOnTodoChange()
                    else -> Unit
                }
            }
            toolEventListenerHandle = conversationOpsProvider.registerToolEventListener()
            conversationOps.loadConversationFromDb()
            conversationOps.resumeInterruptedConversationIfNeeded()
        }

        override fun destroy() {
            runCatching { toolEventListenerHandle?.close() }
            scope.cancel()
        }

        /**
         * Returns all sub-agents from the in-memory map.
         */
        fun getSubAgents(): List<SubAgent> = lifecycleOps.getSubAgents()

        /**
         * Returns a sub-agent by ID from the in-memory map, or null if not found.
         */
        fun getSubAgent(id: String): SubAgent? = lifecycleOps.getSubAgent(id)

        internal fun saveSubAgent(agent: SubAgent) {
            lifecycleOps.saveAgentToDb(agent)
        }

        /**
         * Returns all todos from the database.
         */
        fun getTodosFromDb(): List<Todo> = lifecycleOps.getTodosFromDb()

        /**
         * Returns a summary of todos (counts by status) from the database.
         */
        fun getTodoSummaryFromDb(): TodoSummary = lifecycleOps.getTodoSummaryFromDb()

        /**
         * Creates a new sub-agent with the given name, role, and template.
         */
        fun createAgent(
            name: String,
            role: String,
            templateName: String = "researcher",
        ): SubAgent = lifecycleOps.createAgent(name, role, templateName)

        /**
         * Updates an existing sub-agent's name, role, or config. Returns true if the agent was found and updated.
         */
        fun updateAgent(
            id: String,
            name: String? = null,
            role: String? = null,
            config: AgentConfig? = null,
        ): Boolean = lifecycleOps.updateAgent(id, name, role, config)

        /**
         * Deletes a sub-agent by ID. Returns true if the agent was found and deleted.
         */
        fun deleteAgent(id: String): Boolean = lifecycleOps.deleteAgent(id)

        /**
         * Sends a chat message to a sub-agent and returns its text response.
         */
        suspend fun sendMessageToAgent(
            agentId: String,
            content: String,
        ): String = conversationOps.sendMessageToAgent(agentId, content)

        /**
         * Runs a sub-agent job synchronously (awaits completion) and returns the result.
         */
        suspend fun runAgentJob(
            agentId: String,
            content: String,
        ): AgentJobResult =
            subAgentJobScheduler.run {
                conversationOps.runAgentJob(agentId, content)
            }

        /**
         * Enqueues a sub-agent job for an existing agent and returns the job ID.
         */
        fun enqueueAgentJob(
            agentId: String,
            content: String,
        ): String =
            subAgentJobScheduler.enqueue(
                block = { conversationOps.runAgentJob(agentId, content) },
                onFinished = conversationOps::notifyMainAgentOfJobCompletion,
            )

        /**
         * Creates a temporary sub-agent, runs a job synchronously, and returns the result.
         */
        suspend fun startAgentJob(
            name: String,
            role: String,
            templateName: String,
            content: String,
        ): AgentJobResult =
            subAgentJobScheduler.run {
                conversationOps.startAgentJob(name, role, templateName, content)
            }

        /**
         * Creates a temporary sub-agent, enqueues a job, and returns the job ID.
         */
        fun enqueueAgentJob(
            name: String,
            role: String,
            templateName: String,
            content: String,
        ): String =
            subAgentJobScheduler.enqueue(
                block = { conversationOps.startAgentJob(name, role, templateName, content) },
                onFinished = conversationOps::notifyMainAgentOfJobCompletion,
            )

        /**
         * Returns a snapshot of the current sub-agent job queue.
         */
        fun getSubAgentJobQueueSnapshot(): SubAgentJobQueueSnapshot = subAgentJobScheduler.snapshot()

        /**
         * Returns the number of active jobs for a given agent ID.
         */
        fun getActiveJobCount(agentId: String): Int = activeJobsByAgentId[agentId] ?: 0

        /**
         * Sends a user message to the main agent and returns the assistant response.
         */
        suspend fun sendMessage(
            content: String,
            token: CancellationToken? = null,
        ): String = conversationOps.sendMessage(content, token)

        /**
         * Sends a user message to the main agent and streams the response via [onChunk].
         */
        suspend fun streamMessage(
            content: String,
            token: CancellationToken? = null,
            onChunk: (String) -> Unit,
        ): String = conversationOps.streamMessage(content, token, onChunk)

        /**
         * Cancels a sub-agent job by job ID. Returns true if the job was found and cancelled.
         */
        fun cancelSubAgentJob(jobId: String): Boolean = subAgentJobScheduler.cancelJob(jobId)

        /**
         * Cancels all running sub-agent jobs. Returns the set of cancelled job IDs.
         */
        fun cancelAllRunningActions(): Set<String> = subAgentJobScheduler.cancelAllJobs()

        /**
         * Cancels all active (non-completed, non-cancelled) todos.
         */
        fun cancelAllActiveTodos() {
            todoManager
                .getAll()
                .filter {
                    it.status != de.heckenmann.visualagent.todo.TodoStatus.COMPLETED &&
                        it.status != de.heckenmann.visualagent.todo.TodoStatus.CANCELLED
                }.forEach { todoManager.cancelTodo(it.id) }
        }

        /**
         * Clears the in-memory conversation history.
         */
        fun clearHistory() {
            conversationOps.clearHistory()
        }

        /**
         * Adds a welcome message to the conversation after a history reset.
         */
        suspend fun addWelcomeMessageAfterReset(): de.heckenmann.visualagent.agent.conversation.WelcomeResult =
            conversationOps.addWelcomeMessageAfterReset()

        /**
         * Returns the current in-memory conversation history.
         */
        fun getHistory(): List<Message> = conversationOps.getHistory()

        /**
         * Appends a system message to the conversation history.
         */
        fun appendSystemMessage(content: String) {
            conversationOps.appendSystemMessage(content)
        }

        /**
         * Records a tool call event in the conversation history.
         */
        fun recordToolCall(event: ToolCallEvent) {
            conversationOps.recordToolCall(event)
        }

        /**
         * Deletes a message from the conversation history by its ID.
         */
        fun deleteMessageById(id: String) = conversationOps.deleteMessageById(id)

        /**
         * Updates the content of a message in the conversation history by its ID.
         */
        fun updateMessageContentById(
            id: String,
            newContent: String,
        ) = conversationOps.updateMessageContentById(id, newContent)

        /**
         * Loads older conversation history from the database, paginated by [pageSize].
         */
        fun loadOlderHistory(pageSize: Int = HISTORY_PAGE_SIZE): List<Message> = conversationOps.loadOlderHistory(pageSize)

        /**
         * Returns all sub-agents from the database (bypasses the in-memory cache).
         */
        fun getSubAgentsFromDb(): List<SubAgent> = lifecycleOps.getSubAgentsFromDb()

        /**
         * Seeds the default UX improvement todos if they do not already exist.
         */
        fun seedUxTodos() = autonomyOps.seedUxTodos()

        /**
         * Starts the autonomous todo-processing loop. Optionally seeds UX todos first.
         */
        fun startAutonomousProcessing(seed: Boolean = true) = autonomyOps.startAutonomousProcessing(seed)

        /**
         * Starts autonomous mode with a specific goal, adding it as a todo first.
         */
        fun startAutonomousMode(goal: String) = autonomyOps.startAutonomousMode(goal)

        /**
         * Triggers the main agent to process a todo change notification.
         * The agent's response is persisted to the conversation history.
         */
        internal fun triggerMainAgentOnTodoChange() {
            scope.launch {
                val history = conversationOps.loadRecentHistoryFromDb()
                val request = conversationOps.buildMainRequest(history)
                runCatching {
                    val response = llmProvider.chat(request)
                    val content = responseCoordinator.normalizeAssistantContent(response.message.content)
                    conversationOps.persist(Message(role = "assistant", content = content))
                }
                toolEventBus.publish(
                    ToolCallEvent(
                        toolId = "todos",
                        functionName = "todos",
                        phase = ToolCallPhase.FINISHED,
                        inputJson = "{}",
                        context = mapOf("trigger" to "todoChange"),
                        result = ToolResult(toolId = "todos", success = true, content = ""),
                        startedAtUtc = java.time.Instant.now(),
                        finishedAtUtc = java.time.Instant.now(),
                        durationMillis = 0L,
                    ),
                )
            }
        }
    }
