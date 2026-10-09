package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.ParallelismProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionControl
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import mu.KotlinLogging
import reactor.core.Disposable
import reactor.core.Disposables
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates autonomous todo decomposition and worker execution through conflated
 * event-driven pickup.
 *
 * Use cases: UC-0000014, UC-0000053, UC-0000054, UC-0000055, UC-0000057.
 */
class AutonomousCoordinator
    constructor(
        private val todoManager: TodoManager,
        private val llmProvider: LLMProvider,
        private val todoStore: TodoStore,
        private val memoryStore: MemoryStore,
        private val agentToolConfigService: AgentToolConfigService,
        private val jobScheduler: SubAgentJobScheduler,
        private val parallelismProvider: ParallelismProvider,
        private val todoEventBus: TodoEventBus,
        private val conversationOps: ConversationOpsProvider,
        private val subAgentOps: SubAgentOpsProvider,
        private val executionControl: SubAgentExecutionControl? = null,
        private val retryDelay: (Long) -> Mono<Void> = { Mono.delay(Duration.ofMillis(it)).then() },
        private val toolScopes: () -> ToolExecutionScope = ::ToolExecutionScope,
    ) : AutoCloseable {
        private val logger = KotlinLogging.logger {}
        private val subAgents: Map<String, SubAgent>
            get() = subAgentOps.allSubAgents
        private val pendingTodoChanges = ConcurrentHashMap<String, TodoChange>()
        private val todoLifecycleLock = Any()
        private val activeCancellationTokens = ConcurrentHashMap<String, CancellationToken>()
        private val activeTodoJobs = ConcurrentHashMap<String, Disposable>()
        private val agentBusySince = ConcurrentHashMap<String, Long>()
        private val requestedTodoIds = ConcurrentLinkedQueue<String>()
        private val requestedTodoIdSet = ConcurrentHashMap.newKeySet<String>()
        private val workSignal = AutonomousWorkSignal()
        private val closed = AtomicBoolean(false)
        private val autonomousProcessingEnabled = AtomicBoolean(false)
        private val subscriptions = mutableListOf<AutoCloseable>()
        private val taskPlanner =
            AutonomousTaskPlanner(
                todoManager = todoManager,
                subAgents = subAgents,
                llmProvider = llmProvider,
                agentToolConfigService = agentToolConfigService,
            )
        private val decompositionScheduler =
            AutonomousTodoDecompositionScheduler(
                todoStore = todoStore,
                taskPlanner = taskPlanner,
                jobScheduler = jobScheduler,
                subAgentOps = subAgentOps,
                executionControl = executionControl,
                signalWork = workSignal::signal,
            )
        private val todoControl =
            AutonomousTodoControl(
                todoLifecycleLock = todoLifecycleLock,
                todoManager = todoManager,
                todoStore = todoStore,
                activeCancellationTokens = activeCancellationTokens,
                activeTodoJobs = activeTodoJobs,
                jobScheduler = jobScheduler,
                decompositionScheduler = decompositionScheduler,
                subAgents = { subAgents },
                agentBusySince = agentBusySince,
                subAgentOps = subAgentOps,
            )
        private val candidateSelector =
            AutonomousTodoCandidateSelector(
                todoStore = todoStore,
                subAgents = subAgents,
                taskPlanner = taskPlanner,
                decompositionScheduler = decompositionScheduler,
                executionControl = executionControl,
            )

        init {
            logger.info { "AutonomousCoordinator initialized" }
            val pickup =
                workSignal
                    .events()
                    .concatMap({
                        Mono
                            .fromRunnable<Void> { drainWork() }
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume { error ->
                                logger.warn(error) { "Autonomous work pickup failed; waiting for the next signal" }
                                Mono.empty()
                            }
                    }, 1)
                    .subscribe({}, { error -> logger.error(error) { "Autonomous pickup terminated" } })
            subscriptions += AutoCloseable(pickup::dispose)
            subscriptions +=
                todoEventBus.addListener { change ->
                    val snapshot = change.todo
                    if (snapshot != null &&
                        change.type != de.heckenmann.visualagent.todo.TodoChangeType.REMOVED &&
                        todoManager.getById(snapshot.id)?.timelineSequence != snapshot.timelineSequence
                    ) {
                        return@addListener
                    }
                    change.todo?.id?.let { pendingTodoChanges[it] = change }
                    change.todoId?.let { pendingTodoChanges[it] = change }
                    val todo = change.todo
                    decompositionScheduler.onTodoChanged(change)
                    if (todo?.status == TodoStatus.PENDING) {
                        activeCancellationTokens[todo.id]?.cancel()
                    }
                    if (autonomousProcessingEnabled.get() || requestedTodoIds.isNotEmpty()) workSignal.signal()
                }
            executionControl?.let { control ->
                subscriptions += control.addListener { workSignal.signal() }
            }
            subscriptions += parallelismProvider.addChangeListener { workSignal.signal() }
        }

        /** Releases event subscriptions and cancels active autonomous work. */
        override fun close() {
            closed.set(true)
            subscriptions.forEach(AutoCloseable::close)
            subscriptions.clear()
            activeCancellationTokens.values.forEach(CancellationToken::cancel)
            activeTodoJobs.values.forEach(Disposable::dispose)
            decompositionScheduler.close()
        }

        /**
         * Seeds the default UX improvement todos if they do not already exist in the database.
         */
        fun seedUxTodos() {
            val existingDescriptions = todoStore.listTodos().mapTo(mutableSetOf()) { it.description }
            UxSeedTasks.all().filterNot { it in existingDescriptions }.forEach { desc -> todoManager.add(desc) }
        }

        /** Requests a pickup pass after worker availability or configuration changes. */
        fun signalWork() {
            workSignal.signal()
        }

        /**
         * Enables event-driven autonomous todo processing. Optionally seeds UX todos first.
         */
        fun startAutonomousProcessing(seed: Boolean = true) {
            autonomousProcessingEnabled.set(true)
            if (seed) seedUxTodos()
            workSignal.signal()
        }

        /**
         * Starts autonomous mode with a specific goal, adding it as a todo and beginning
         * the processing loop without seeding UX todos.
         */
        fun startAutonomousMode(goal: String) {
            if (goal.isNotBlank()) todoManager.add(goal.trim())
            startAutonomousProcessing(seed = false)
        }

        /**
         * Queues one todo for autonomous execution without enabling unrelated pending work.
         * Cancelled todos are reset to pending; completed todos are not restarted.
         *
         * @param todoId Identifier of the todo to start
         * @return true when the todo can be started
         */
        fun startTodo(todoId: String): Boolean {
            val todo = todoStore.listTodos().firstOrNull { it.id == todoId } ?: return false
            if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.IN_PROGRESS) return false
            if (!requestedTodoIdSet.add(todoId)) return false
            try {
                if (todo.status == TodoStatus.CANCELLED) todoManager.updateStatus(todoId, TodoStatus.PENDING)
                requestedTodoIds.add(todoId)
                workSignal.signal()
            } catch (error: Exception) {
                requestedTodoIdSet.remove(todoId)
                throw error
            }
            return true
        }

        /**
         * Queues every unfinished todo for autonomous execution.
         *
         * @return Number of todos newly queued for execution
         */
        fun startAllTodos(): Int {
            val startableTodos = todoStore.listTodos().filter { it.status == TodoStatus.PENDING || it.status == TodoStatus.CANCELLED }
            startableTodos.filter { it.status == TodoStatus.CANCELLED }.forEach {
                todoManager.updateStatus(it.id, TodoStatus.PENDING)
            }
            if (startableTodos.isNotEmpty()) startAutonomousProcessing(seed = false)
            return startableTodos.size
        }

        /**
         * Stops one unfinished todo and cancels its in-flight worker cooperatively.
         *
         * @param todoId Identifier of the todo to stop
         * @return true when the todo was cancelled
         */
        fun stopTodo(todoId: String): Boolean = todoControl.stopTodo(todoId)

        /**
         * Stops every unfinished todo and cancels active workers cooperatively.
         *
         * @return Number of todos cancelled
         */
        fun stopAllTodos(): Int = todoControl.stopAllTodos()

        /**
         * Cancels the in-progress todo assigned to the given agent.
         */
        fun cancelAgentTodo(
            agentId: String,
            removedAgent: SubAgent? = null,
        ) = todoControl.cancelAgentTodo(agentId, removedAgent)

        /** Serializes agent removal with candidate selection and todo claiming. */
        internal fun <T> withTodoLifecycleLock(action: () -> T): T = synchronized(todoLifecycleLock, action)

        private fun drainWork() {
            if (executionControl?.isGloballyPaused() == true) return
            candidateSelector
                .orderRequested(requestedTodoIds.toList())
                .forEach { requestedTodoId ->
                    val claimed = claimAndProcessOneTodo(requestedTodoId)
                    val current = todoStore.listTodos().firstOrNull { it.id == requestedTodoId }
                    if (!claimed && current?.status != TodoStatus.PENDING) {
                        requestedTodoIds.remove(requestedTodoId)
                        requestedTodoIdSet.remove(requestedTodoId)
                    }
                }
            if (autonomousProcessingEnabled.get()) {
                while (claimAndProcessOneTodo()) {
                    // Continue claiming while capacity is available.
                }
                synchronized(todoLifecycleLock) {
                    decompositionScheduler.scheduleIfNeeded(autonomousProcessingEnabled.get())
                }
            }
        }

        private fun claimAndProcessOneTodo(requestedTodoId: String? = null): Boolean =
            synchronized(todoLifecycleLock) { claimAndProcessOneTodoUnderLock(requestedTodoId) }

        private fun claimAndProcessOneTodoUnderLock(requestedTodoId: String?): Boolean {
            if (executionControl?.isGloballyPaused() == true) return false
            val busyCount =
                subAgents.values.count {
                    it.status == AgentStatus.BUSY && executionControl?.isExecutionAllowed(it.id) != false
                }
            if (busyCount >= parallelismProvider.get().coerceAtLeast(1)) return false

            val candidate = candidateSelector.find(requestedTodoId) ?: return false
            val agent = candidate.agent
            val requestId = conversationOps.beginConversationRequest()
            val todo = todoManager.claimPendingTodo(candidate.todo.id, agent.id) ?: return false
            requestedTodoIds.remove(todo.id)
            requestedTodoIdSet.remove(todo.id)
            try {
                agent.status = AgentStatus.BUSY
                agent.currentTodoId = todo.id
                agent.currentTask = todo.description
                agentBusySince[agent.id] = System.currentTimeMillis()
                subAgentOps.saveSubAgent(agent)
                persistTodoStart(agent, todo, requestId, conversationOps::persist)
                subAgentOps.notifyAgent(agent.id, "STATUS:${agent.status.name}")

                val token = CancellationToken().also { activeCancellationTokens[todo.id] = it }
                val processingJob = Disposables.swap()
                activeTodoJobs[todo.id] = processingJob
                val pipeline =
                    Mono
                        .defer {
                            if (todoManager.getById(todo.id)?.status != TodoStatus.IN_PROGRESS) {
                                if (activeCancellationTokens.remove(todo.id, token)) {
                                    releaseAutonomousTodoAgent(agent, todo.id, agentBusySince, subAgentOps)
                                }
                                Mono.empty()
                            } else {
                                AutonomousTodoProcessor(
                                    agent = agent,
                                    todoId = todo.id,
                                    taskDescription = taskPlanner.buildWorkerInstruction(todo),
                                    claimedTodo = todo,
                                    llmProvider = llmProvider,
                                    memoryStore = memoryStore,
                                    agentToolConfigService = agentToolConfigService,
                                    taskPlanner = taskPlanner,
                                    conversationOps = conversationOps,
                                    todoManager = todoManager,
                                    subAgentOps = subAgentOps,
                                    activeCancellationTokens = activeCancellationTokens,
                                    agentBusySince = agentBusySince,
                                    pendingTodoChanges = pendingTodoChanges,
                                    todoEventBus = todoEventBus,
                                    isActive = { !closed.get() },
                                    jobScheduler = jobScheduler,
                                    executionControl = executionControl,
                                    cancellationToken = token,
                                    conversationRequestId = requestId,
                                    retryDelay = retryDelay,
                                    toolScopes = toolScopes,
                                    onRetryPending = { startTodo(it) },
                                    onCleanup = workSignal::signal,
                                ).execute()
                            }
                        }.doFinally {
                            activeTodoJobs.remove(todo.id, processingJob)
                            workSignal.signal()
                        }
                processingJob.update(
                    pipeline.subscribeOn(Schedulers.boundedElastic()).subscribe(
                        {},
                        { error -> logger.warn(error) { "Autonomous todo ${todo.id} terminated" } },
                    ),
                )
                return true
            } catch (error: Throwable) {
                releaseAutonomousTodoAgent(agent, todo.id, agentBusySince, subAgentOps)
                if (todoManager.update(TodoUpdateCommand(todo.id, status = TodoStatus.PENDING), expected = todo)) startTodo(todo.id)
                throw error
            }
        }
    }
