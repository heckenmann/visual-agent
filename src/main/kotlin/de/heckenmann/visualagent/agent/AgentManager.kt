package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps
import de.heckenmann.visualagent.agent.text.AgentResponseCoordinator
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.PersistenceStores
import de.heckenmann.visualagent.knowledge.SubAgentStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.orchestration.AutonomousCoordinator
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Main orchestration facade for chat, history, todos, and sub-agent coordination.
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
    ) : DisposableBean {
        internal constructor(
            stores: PersistenceStores,
            llmProvider: LLMProvider,
            agentToolConfigService: AgentToolConfigService,
            toolEventBus: ToolEventBus,
        ) : this(
            stores,
            stores,
            stores,
            stores,
            llmProvider,
            agentToolConfigService,
            toolEventBus,
        )

        companion object {
            internal const val MAIN_SESSION_ID = "main"
            internal const val INITIAL_HISTORY_LOAD_LIMIT = 20
            internal const val HISTORY_PAGE_SIZE = 20
            internal const val REPETITION_GUARD_RETRY_LIMIT = 1
            private var globalAgentCallback: ((String, String) -> Unit)? = null

            /**
             * Registers the UI callback that receives sub-agent lifecycle notifications.
             *
             * @param callback Callback invoked with agent ID and user-facing message
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
                initialTodos = todoStore.listTodos(),
                onChange = { change -> lifecycleOps.persistTodoChange(change) },
            )
        internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        internal val subAgentJobScheduler =
            SubAgentJobScheduler(scope) {
                AppConfig.instance.maxParallelSubAgents
            }
        internal val subAgents = mutableMapOf<String, SubAgent>()
        internal val activeJobsByAgentId = ConcurrentHashMap<String, Int>()
        internal val conversationHistory = mutableListOf<Message>()
        internal var pendingResumeMessage: String? = null
        internal var loadedHistoryCount: Int = 0
        internal val finishedToolEventsByRequestId = ConcurrentHashMap<String, MutableList<ToolCallEvent>>()
        private lateinit var toolEventListenerHandle: AutoCloseable
        internal lateinit var autonomousCoordinator: AutonomousCoordinator
        internal lateinit var responseCoordinator: AgentResponseCoordinator
        private val lifecycleOps = AgentManagerLifecycleOps(this)
        private val conversationOps = AgentManagerConversationOps(this)
        private val autonomyOps = AgentManagerAutonomyOps(this)

        init {
            responseCoordinator =
                AgentResponseCoordinator(
                    llmProvider = llmProvider,
                    mainSessionId = MAIN_SESSION_ID,
                    repetitionGuardRetryLimit = REPETITION_GUARD_RETRY_LIMIT,
                    finishedToolEventsByRequestId = finishedToolEventsByRequestId,
                    buildMainRequest = conversationOps::buildMainRequest,
                    buildMainSystemContextPrompt = conversationOps::buildMainSystemContextPrompt,
                    loadRecentHistoryFromDb = conversationOps::loadRecentHistoryFromDb,
                )
            autonomousCoordinator =
                AutonomousCoordinator(
                    scope = scope,
                    todoManager = todoManager,
                    subAgents = subAgents,
                    llmProvider = llmProvider,
                    todoStore = todoStore,
                    memoryStore = memoryStore,
                    agentToolConfigService = agentToolConfigService,
                    jobScheduler = subAgentJobScheduler,
                    createAgent = { name, role, templateName -> lifecycleOps.createAgent(name, role, templateName) },
                    saveAgentToDb = lifecycleOps::saveAgentToDb,
                    notifyAgent = Companion::notifyAgent,
                )
            toolEventListenerHandle = conversationOps.registerToolEventListener()
            lifecycleOps.loadAgentsFromDb()
            conversationOps.loadConversationFromDb()
            conversationOps.resumeInterruptedConversationIfNeeded()
        }

        /**
         * Releases tool listeners before bean destruction.
         */
        override fun destroy() {
            runCatching { toolEventListenerHandle.close() }
            scope.cancel()
        }

        /** Returns the current in-memory sub-agent snapshot. */
        fun getSubAgents(): List<SubAgent> = lifecycleOps.getSubAgents()

        /** Returns one sub-agent by its stable identifier. */
        fun getSubAgent(id: String): SubAgent? = lifecycleOps.getSubAgent(id)

        internal fun saveSubAgent(agent: SubAgent) {
            lifecycleOps.saveAgentToDb(agent)
        }

        /** Loads the authoritative todo list from persistence. */
        fun getTodosFromDb(): List<Todo> = lifecycleOps.getTodosFromDb()

        /** Returns authoritative todo counters from persistence. */
        fun getTodoSummaryFromDb(): TodoSummary = lifecycleOps.getTodoSummaryFromDb()

        /** Creates and persists a sub-agent from the requested template. */
        fun createAgent(
            name: String,
            role: String,
            templateName: String = "researcher",
        ): SubAgent = lifecycleOps.createAgent(name, role, templateName)

        /** Updates mutable sub-agent metadata and configuration. */
        fun updateAgent(
            id: String,
            name: String? = null,
            role: String? = null,
            config: AgentConfig? = null,
        ): Boolean = lifecycleOps.updateAgent(id, name, role, config)

        /** Deletes a sub-agent and its persisted tool configuration. */
        fun deleteAgent(id: String): Boolean = lifecycleOps.deleteAgent(id)

        /** Sends a conversational message directly to an existing sub-agent. */
        suspend fun sendMessageToAgent(
            agentId: String,
            content: String,
        ): String = conversationOps.sendMessageToAgent(agentId, content)

        /** Runs a synchronous job on an existing sub-agent under the concurrency limit. */
        suspend fun runAgentJob(
            agentId: String,
            content: String,
        ): AgentJobResult =
            subAgentJobScheduler.run {
                conversationOps.runAgentJob(agentId, content)
            }

        /** Queues an asynchronous job for an existing sub-agent. */
        fun enqueueAgentJob(
            agentId: String,
            content: String,
        ): String =
            subAgentJobScheduler.enqueue(
                block = { conversationOps.runAgentJob(agentId, content) },
                onFinished = conversationOps::notifyMainAgentOfJobCompletion,
            )

        /** Creates a sub-agent and runs a synchronous job under the concurrency limit. */
        suspend fun startAgentJob(
            name: String,
            role: String,
            templateName: String,
            content: String,
        ): AgentJobResult =
            subAgentJobScheduler.run {
                conversationOps.startAgentJob(name, role, templateName, content)
            }

        /** Queues creation and asynchronous execution of a sub-agent job. */
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

        /** Returns active and queued sub-agent job counts. */
        fun getSubAgentJobQueueSnapshot(): SubAgentJobQueueSnapshot = subAgentJobScheduler.snapshot()

        /**
         * Returns the number of jobs currently executing for one sub-agent.
         *
         * @param agentId Stable sub-agent identifier
         * @return Active execution count
         */
        fun getActiveJobCount(agentId: String): Int = activeJobsByAgentId[agentId] ?: 0

        /** Sends a message to the main agent and persists both conversation turns. */
        suspend fun sendMessage(content: String): String = conversationOps.sendMessage(content)

        /** Streams a main-agent response while persisting the completed turn. */
        suspend fun streamMessage(
            content: String,
            onChunk: (String) -> Unit,
        ): String = conversationOps.streamMessage(content, onChunk)

        /** Deletes the main conversation history from memory and persistence. */
        fun clearHistory() {
            conversationOps.clearHistory()
        }

        /** Generates and persists the post-reset welcome message. */
        suspend fun addWelcomeMessageAfterReset(): String = conversationOps.addWelcomeMessageAfterReset()

        /** Returns the currently loaded main conversation history. */
        fun getHistory(): List<Message> = conversationOps.getHistory()

        /** Persists a completed tool-call event as conversation history. */
        fun recordToolCall(event: ToolCallEvent) {
            conversationOps.recordToolCall(event)
        }

        /** Loads an older page and prepends it to the in-memory history. */
        fun loadOlderHistory(pageSize: Int = HISTORY_PAGE_SIZE): List<Message> = conversationOps.loadOlderHistory(pageSize)

        /** Loads all persisted sub-agent records. */
        fun getSubAgentsFromDb(): List<SubAgent> = lifecycleOps.getSubAgentsFromDb()

        /** Assigns the next pending todo to an available worker. */
        fun assignNextTodo(): Boolean = autonomyOps.assignNextTodo()

        /** Assigns one todo to a specific sub-agent. */
        fun assignTodoToAgent(
            todoId: String,
            agentId: String,
        ): Boolean = autonomyOps.assignTodoToAgent(todoId, agentId)

        /** Assigns as many pending todos as current capacity permits. */
        fun assignAllPendingTodos(): Int = autonomyOps.assignAllPendingTodos()

        /** Adds the predefined UX improvement tasks when they are absent. */
        fun seedUxTodos() = autonomyOps.seedUxTodos()

        /** Starts background autonomous todo processing. */
        fun startAutonomousProcessing(seed: Boolean = true) = autonomyOps.startAutonomousProcessing(seed)

        /** Starts autonomous processing for a newly created top-level goal. */
        fun startAutonomousMode(goal: String) = autonomyOps.startAutonomousMode(goal)
    }
