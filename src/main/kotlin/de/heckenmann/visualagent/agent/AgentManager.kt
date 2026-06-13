package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.autonomy.AutonomousCoordinator
import de.heckenmann.visualagent.agent.text.AgentResponseCoordinator
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.PersistenceStores
import de.heckenmann.visualagent.knowledge.SubAgentStore
import de.heckenmann.visualagent.knowledge.TodoStore
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

        fun getSubAgents(): List<SubAgent> = lifecycleOps.getSubAgents()

        fun getSubAgent(id: String): SubAgent? = lifecycleOps.getSubAgent(id)

        internal fun saveSubAgent(agent: SubAgent) {
            lifecycleOps.saveAgentToDb(agent)
        }

        fun getTodosFromDb(): List<Todo> = lifecycleOps.getTodosFromDb()

        fun getTodoSummaryFromDb(): TodoSummary = lifecycleOps.getTodoSummaryFromDb()

        fun createAgent(
            name: String,
            role: String,
            templateName: String = "researcher",
        ): SubAgent = lifecycleOps.createAgent(name, role, templateName)

        fun updateAgent(
            id: String,
            name: String? = null,
            role: String? = null,
            config: AgentConfig? = null,
        ): Boolean = lifecycleOps.updateAgent(id, name, role, config)

        fun deleteAgent(id: String): Boolean = lifecycleOps.deleteAgent(id)

        suspend fun sendMessageToAgent(
            agentId: String,
            content: String,
        ): String = conversationOps.sendMessageToAgent(agentId, content)

        suspend fun runAgentJob(
            agentId: String,
            content: String,
        ): AgentJobResult =
            subAgentJobScheduler.run {
                conversationOps.runAgentJob(agentId, content)
            }

        fun enqueueAgentJob(
            agentId: String,
            content: String,
        ): String =
            subAgentJobScheduler.enqueue(
                block = { conversationOps.runAgentJob(agentId, content) },
                onFinished = conversationOps::notifyMainAgentOfJobCompletion,
            )

        suspend fun startAgentJob(
            name: String,
            role: String,
            templateName: String,
            content: String,
        ): AgentJobResult =
            subAgentJobScheduler.run {
                conversationOps.startAgentJob(name, role, templateName, content)
            }

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

        fun getSubAgentJobQueueSnapshot(): SubAgentJobQueueSnapshot = subAgentJobScheduler.snapshot()

        /**
         * Returns the number of jobs currently executing for one sub-agent.
         *
         * @param agentId Stable sub-agent identifier
         * @return Active execution count
         */
        fun getActiveJobCount(agentId: String): Int = activeJobsByAgentId[agentId] ?: 0

        suspend fun sendMessage(content: String): String = conversationOps.sendMessage(content)

        suspend fun streamMessage(
            content: String,
            onChunk: (String) -> Unit,
        ): String = conversationOps.streamMessage(content, onChunk)

        fun clearHistory() {
            conversationOps.clearHistory()
        }

        suspend fun addWelcomeMessageAfterReset(): String = conversationOps.addWelcomeMessageAfterReset()

        fun getHistory(): List<Message> = conversationOps.getHistory()

        fun recordToolCall(event: ToolCallEvent) {
            conversationOps.recordToolCall(event)
        }

        fun loadOlderHistory(pageSize: Int = HISTORY_PAGE_SIZE): List<Message> = conversationOps.loadOlderHistory(pageSize)

        fun getSubAgentsFromDb(): List<SubAgent> = lifecycleOps.getSubAgentsFromDb()

        fun assignNextTodo(): Boolean = autonomyOps.assignNextTodo()

        fun assignTodoToAgent(
            todoId: String,
            agentId: String,
        ): Boolean = autonomyOps.assignTodoToAgent(todoId, agentId)

        fun assignAllPendingTodos(): Int = autonomyOps.assignAllPendingTodos()

        fun seedUxTodos() = autonomyOps.seedUxTodos()

        fun startAutonomousProcessing(seed: Boolean = true) = autonomyOps.startAutonomousProcessing(seed)

        fun startAutonomousMode(goal: String) = autonomyOps.startAutonomousMode(goal)
    }
