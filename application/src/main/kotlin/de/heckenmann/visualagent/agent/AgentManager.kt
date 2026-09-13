package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps
import de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage
import de.heckenmann.visualagent.agent.conversation.WelcomeMessageComposer
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.text.AgentResponseCoordinator
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.knowledge.InMemoryMainAgentLongTermMemoryStore
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.PersistenceStores
import de.heckenmann.visualagent.knowledge.SubAgentStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.orchestration.AutonomousCoordinator
import de.heckenmann.visualagent.protocol.ConversationCompletionEventBus
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.protocol.LifecycleState
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
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
        internal val mainAgentLongTermMemoryStore: MainAgentLongTermMemoryStore,
        val llmProvider: LLMProvider,
        internal val agentToolConfigService: AgentToolConfigService,
        internal val toolEventBus: ToolEventBus,
        internal val todoEventBus: TodoEventBus,
        internal val appConfig: AppConfigBean,
        internal val scope: CoroutineScope,
        internal val lifecycle: LifecyclePort,
        internal val parallelismProvider: ParallelismProvider,
        internal val agentStatusCallbackAdapter: AgentStatusCallbackAdapter,
        val subAgentExecutionControl: SubAgentExecutionControl,
        internal val providerCatalog: ProviderCatalogService,
        internal val conversationCompletionEvents: ConversationCompletionEventBus,
    ) : DisposableBean {
        internal constructor(
            stores: PersistenceStores,
            llmProvider: LLMProvider,
            agentToolConfigService: AgentToolConfigService,
            toolEventBus: ToolEventBus,
            todoEventBus: TodoEventBus,
            appConfig: AppConfigBean,
            mainAgentLongTermMemoryStore: MainAgentLongTermMemoryStore = InMemoryMainAgentLongTermMemoryStore(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            lifecycle: LifecyclePort = LifecycleState(),
            parallelismProvider: ParallelismProvider = ParallelismProvider(appConfig),
            agentStatusCallbackAdapter: AgentStatusCallbackAdapter = AgentStatusCallbackAdapter(),
            subAgentExecutionControl: SubAgentExecutionControl = SubAgentExecutionControl(stores),
            providerCatalog: ProviderCatalogService = ProviderCatalogService(stores, appConfig),
            conversationCompletionEvents: ConversationCompletionEventBus = ConversationCompletionEventBus(),
        ) : this(
            stores,
            stores,
            stores,
            stores,
            mainAgentLongTermMemoryStore,
            llmProvider,
            agentToolConfigService,
            toolEventBus,
            todoEventBus,
            appConfig,
            scope,
            lifecycle,
            parallelismProvider,
            agentStatusCallbackAdapter,
            subAgentExecutionControl,
            providerCatalog,
            conversationCompletionEvents,
        )

        internal lateinit var autonomousCoordinator: AutonomousCoordinator
        internal lateinit var responseCoordinator: AgentResponseCoordinator
        internal var todoManager: TodoManager = TodoManager(todoStore, todoEventBus)
        internal val welcomeMessageComposer = WelcomeMessageComposer(llmProvider, appConfig, providerCatalog)
        internal val subAgentJobScheduler = SubAgentJobScheduler(scope, parallelismProvider, subAgentExecutionControl)
        internal val conversationOpsProvider = ConversationOpsProvider(toolEventBus)
        internal val subAgentOpsProvider = SubAgentOpsProvider()
        internal val subAgents: Map<String, SubAgent> get() = subAgentOpsProvider.allSubAgents
        internal val activeJobsByAgentId = ConcurrentHashMap<String, Int>()
        internal val conversationHistory = mutableListOf<Message>()
        internal var pendingResumeMessage: String? = null
        internal var loadedHistoryCount: Int = 0
        internal val finishedToolEventsByRequestId = ConcurrentHashMap<String, MutableList<ToolCallEvent>>()
        private var toolEventListenerHandle: AutoCloseable? = null

        private val lifecycleOps = AgentManagerLifecycleOps(this)
        internal val conversationOps = AgentManagerConversationOps(this)
        internal val autonomyOps = AgentManagerAutonomyOps(this)
        internal lateinit var todoTrigger: AgentTodoTrigger

        init {
            lifecycleOps.loadAgentsFromDb()
            todoManager.loadInitialTodos()
            todoManager.addListener { change -> lifecycleOps.persistTodoChange(change) }
            conversationOpsProvider.setBuildMainRequest(conversationOps::buildMainRequest)
            conversationOpsProvider.setBuildMainSystemContextPrompt(conversationOps::buildMainSystemContextPrompt)
            conversationOpsProvider.setLoadRecentHistoryFromDb(conversationOps::loadRecentHistoryFromDb)
            conversationOpsProvider.setLoadMainAgentContextFromDb(conversationOps::loadMainAgentContextFromDb)
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
                    executionControl = subAgentExecutionControl,
                )
            todoTrigger =
                AgentTodoTrigger(
                    scope = scope,
                    conversationOps = conversationOps,
                    llmProvider = llmProvider,
                    responseCoordinator = responseCoordinator,
                    toolEventBus = toolEventBus,
                    lifecycle = lifecycle,
                    completionEvents = conversationCompletionEvents,
                )
            registerTodoTerminalReviewListener()
            toolEventListenerHandle = conversationOpsProvider.registerToolEventListener()
            conversationOps.loadConversationFromDb()
            conversationOps.resumeInterruptedConversationIfNeeded()
        }

        override fun destroy() {
            lifecycle.beginShutdown()
            runCatching { toolEventListenerHandle?.close() }.also { scope.cancel() }
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

        /** Returns a summary of todo counts from the database. */
        fun getTodoSummaryFromDb(): TodoSummary = lifecycleOps.getTodoSummaryFromDb()

        /**
         * Creates a new sub-agent with the given name, role, and template.
         */
        fun createAgent(
            name: String,
            role: String,
            templateName: String = "researcher",
        ): SubAgent =
            lifecycleOps.createAgent(name, role, templateName).also {
                autonomousCoordinator.signalWork()
            }

        /**
         * Updates an existing sub-agent's name, role, or config. Returns true if the agent was found and updated.
         */
        fun updateAgent(
            id: String,
            name: String? = null,
            role: String? = null,
            config: AgentConfig? = null,
        ): Boolean =
            lifecycleOps.updateAgent(id, name, role, config).also { updated ->
                if (updated) autonomousCoordinator.signalWork()
            }

        /**
         * Deletes a sub-agent by ID. Returns true if the agent was found and deleted.
         */
        fun deleteAgent(id: String): Boolean = lifecycleOps.deleteAgent(id)

        /** Returns all sub-agents directly from the database. */
        fun getSubAgentsFromDb(): List<SubAgent> = lifecycleOps.getSubAgentsFromDb()

        /**
         * Returns a snapshot of the current sub-agent job queue.
         */
        fun getSubAgentJobQueueSnapshot(): SubAgentJobQueueSnapshot = subAgentJobScheduler.snapshot()

        /**
         * Sends a user message to the main agent and returns the assistant response.
         */
        suspend fun sendMessage(
            content: String,
            token: CancellationToken? = null,
        ): String = conversationOps.sendMessage(content, token)

        /** Streams a user message while preserving caller-provided conversation entry identities. */
        suspend fun streamMessage(
            content: String,
            token: CancellationToken? = null,
            onChunk: (String) -> Unit,
            userEntryId: String,
            assistantEntryId: String,
        ): String = conversationOps.streamMessage(content, token, onChunk, userEntryId, assistantEntryId)

        /**
         * Cancels all running sub-agent jobs. Returns the set of cancelled job IDs.
         */
        fun cancelAllRunningActions(): Set<String> = subAgentJobScheduler.cancelAllJobs()

        /**
         * Cancels all active (non-completed, non-cancelled) todos.
         */
        fun cancelAllActiveTodos() = lifecycleOps.cancelAllActiveTodos()

        /**
         * Cancels all work owned by the manager before the application context is closed.
         * Cancelling the manager scope first prevents cancellation finalizers from persisting
         * agent state after the persistence layer has started shutting down.
         */
        fun cancelActiveWork() = lifecycleOps.cancelActiveWork()

        /**
         * Clears the in-memory conversation history.
         */
        fun clearHistory() = conversationOps.clearHistory()

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
        fun appendSystemMessage(
            content: String,
            metadata: String? = null,
            contextPolicy: ConversationContextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
        ) {
            conversationOps.persist(Message(role = "system", content = content, metadata = metadata, contextPolicy = contextPolicy))
        }

        /**
         * Records a tool call event in the conversation history.
         */
        fun recordToolCall(event: ToolCallEvent) = conversationOps.recordToolCall(event)

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
        fun loadOlderHistory(pageSize: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> =
            conversationOps.loadOlderHistory(pageSize)

        /** Returns an immutable older page without changing the active in-memory history. */
        fun readOlderHistoryPage(
            offset: Int,
            pageSize: Int = AgentManagerConstants.HISTORY_PAGE_SIZE,
        ): ConversationHistoryPage = conversationOps.readOlderHistoryPage(offset, pageSize)

        /** Returns an immutable latest page without changing the active in-memory history. */
        fun readLatestHistoryPage(limit: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): ConversationHistoryPage =
            conversationOps.readLatestHistoryPage(limit)

        /**
         * Loads the latest messages from the database and appends any that are
         * missing from the in-memory history.
         *
         * @return the updated history list (newest messages appended).
         */
        fun loadLatestHistory(limit: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> =
            conversationOps.loadLatestHistory(limit)

        /**
         * Clears the in-memory conversation history and reloads the latest page
         * from the database. Used by the scroll-to-bottom button to guarantee
         * the user lands on the newest persisted message without paging through
         * intermediate chunks.
         *
         * @return the reloaded history list.
         */
        fun refreshHistoryToLatest(limit: Int = AgentManagerConstants.HISTORY_PAGE_SIZE): List<Message> =
            conversationOps.refreshHistoryToLatest(limit)

        /** Seeds the default UX improvement todos when they are not already present. */
        fun seedUxTodos() = autonomyOps.seedUxTodos()

        /** Starts autonomous todo processing, optionally seeding the default backlog. */
        fun startAutonomousProcessing(seed: Boolean = true) = autonomyOps.startAutonomousProcessing(seed)

        /** Starts autonomous mode with a user-provided goal. */
        fun startAutonomousMode(goal: String) = autonomyOps.startAutonomousMode(goal)
    }
