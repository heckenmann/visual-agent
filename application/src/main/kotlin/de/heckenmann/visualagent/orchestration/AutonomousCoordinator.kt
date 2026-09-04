package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ParallelismProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionControl
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mu.KotlinLogging
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
        private val scope: CoroutineScope,
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
    ) {
        private val logger = KotlinLogging.logger {}
        private val subAgents: Map<String, SubAgent>
            get() = subAgentOps.allSubAgents
        private val pendingTodoChanges = ConcurrentHashMap<String, TodoChange>()
        private val activeCancellationTokens = ConcurrentHashMap<String, CancellationToken>()
        private val activeTodoJobs = ConcurrentHashMap<String, Job>()
        private val agentBusySince = ConcurrentHashMap<String, Long>()
        private val requestedTodoIds = ConcurrentLinkedQueue<String>()
        private val requestedTodoIdSet = ConcurrentHashMap.newKeySet<String>()
        private val workSignal = AutonomousWorkSignal()
        private val autonomousProcessingEnabled = AtomicBoolean(false)
        private val taskPlanner =
            AutonomousTaskPlanner(
                todoManager = todoManager,
                subAgents = subAgents,
                llmProvider = llmProvider,
                agentToolConfigService = agentToolConfigService,
                createAgent = { name, role, templateName -> subAgentOps.createAgent(name, role, templateName) },
            )
        private val decompositionScheduler =
            AutonomousTodoDecompositionScheduler(
                scope = scope,
                todoStore = todoStore,
                taskPlanner = taskPlanner,
                jobScheduler = jobScheduler,
                subAgentOps = subAgentOps,
                executionControl = executionControl,
                signalWork = workSignal::signal,
            )

        init {
            logger.info { "AutonomousCoordinator initialized" }
            scope.launch(Dispatchers.IO) {
                while (true) {
                    workSignal.await()
                    try {
                        drainWork()
                    } catch (error: Throwable) {
                        if (error !is kotlinx.coroutines.CancellationException) {
                            logger.warn(error) { "Autonomous work pickup failed; waiting for the next signal" }
                        }
                        if (error is kotlinx.coroutines.CancellationException) throw error
                    }
                }
            }
            todoEventBus.addListener { change ->
                change.todo?.id?.let { pendingTodoChanges[it] = change }
                change.todoId?.let { pendingTodoChanges[it] = change }
                val todo = change.todo
                decompositionScheduler.onTodoChanged(change)
                if (todo?.status == TodoStatus.PENDING) {
                    activeCancellationTokens[todo.id]?.cancel()
                }
                if (autonomousProcessingEnabled.get() || requestedTodoIds.isNotEmpty()) workSignal.signal()
            }
            executionControl?.addListener { workSignal.signal() }
            parallelismProvider.addChangeListener { workSignal.signal() }
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
        fun stopTodo(todoId: String): Boolean {
            val todo = todoStore.listTodos().firstOrNull { it.id == todoId } ?: return false
            if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.CANCELLED) return false
            activeCancellationTokens[todoId]?.cancel()
            activeTodoJobs[todoId]?.cancel()
            decompositionScheduler.cancel(todoId)
            return todoManager.cancelTodo(todoId)
        }

        /**
         * Stops every unfinished todo and cancels active workers cooperatively.
         *
         * @return Number of todos cancelled
         */
        fun stopAllTodos(): Int {
            val stoppableTodos = todoStore.listTodos().filter { it.status == TodoStatus.PENDING || it.status == TodoStatus.IN_PROGRESS }
            stoppableTodos.forEach { todo ->
                activeCancellationTokens[todo.id]?.cancel()
                activeTodoJobs[todo.id]?.cancel()
                decompositionScheduler.cancel(todo.id)
                todoManager.cancelTodo(todo.id)
            }
            return stoppableTodos.size
        }

        /**
         * Cancels the in-progress todo assigned to the given agent.
         */
        fun cancelAgentTodo(agentId: String) {
            val agent = subAgents[agentId] ?: return
            val todoId = agent.currentTodoId ?: return
            val todo = todoStore.listTodos().firstOrNull { it.id == todoId } ?: return
            if (todo.status != TodoStatus.IN_PROGRESS) return
            persistSubAgentMessage(
                agent = agent,
                content = "Cancelled todo $todoId for deleted agent $agentId.",
                success = false,
                persistMessage = { conversationOps.persist(it) },
            )
            todoManager.cancelTodo(todoId, TodoTerminalReason.AGENT_REMOVED)
        }

        private suspend fun drainWork() {
            if (executionControl?.isGloballyPaused() == true) return
            requestedTodoIds.toList().forEach { requestedTodoId ->
                val claimed = claimAndProcessOneTodo(requestedTodoId)
                val current = todoStore.listTodos().firstOrNull { it.id == requestedTodoId }
                if (claimed || current?.status != TodoStatus.PENDING) {
                    requestedTodoIds.remove(requestedTodoId)
                    requestedTodoIdSet.remove(requestedTodoId)
                }
            }
            if (autonomousProcessingEnabled.get()) {
                while (claimAndProcessOneTodo()) {
                    // Continue claiming while capacity is available.
                }
                decompositionScheduler.scheduleIfNeeded(autonomousProcessingEnabled.get())
            }
        }

        private fun claimAndProcessOneTodo(requestedTodoId: String? = null): Boolean {
            if (executionControl?.isGloballyPaused() == true) return false
            val busyCount =
                subAgents.values.count {
                    it.status == AgentStatus.BUSY && executionControl?.isExecutionAllowed(it.id) != false
                }
            if (busyCount >= parallelismProvider.get().coerceAtLeast(1)) return false

            val candidate = findNextAssignableTodo(requestedTodoId) ?: return false
            val agent = candidate.agent
            val todo = todoManager.claimPendingTodo(candidate.todo.id, agent.id) ?: return false
            try {
                agent.status = AgentStatus.BUSY
                agent.currentTodoId = todo.id
                agent.currentTask = todo.description
                agentBusySince[agent.id] = System.currentTimeMillis()
                subAgentOps.saveSubAgent(agent)
                conversationOps.persist(
                    Message(
                        role = "system",
                        content = "Started todo ${todo.id} (${todo.description.take(80)}) with agent ${agent.id} (${agent.name}).",
                    ),
                )
                subAgentOps.notifyAgent(agent.id, "STATUS:${agent.status.name}")

                val token = CancellationToken().also { activeCancellationTokens[todo.id] = it }
                val processingJob =
                    scope.launch(start = CoroutineStart.LAZY) {
                        if (token.isCancelled || todoManager.getById(todo.id)?.status != TodoStatus.IN_PROGRESS) {
                            activeCancellationTokens.remove(todo.id, token)
                            return@launch
                        }
                        processTodoWithLLM(
                            agent = agent,
                            todoId = todo.id,
                            taskDescription = taskPlanner.buildWorkerInstruction(todo),
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
                            scope = scope,
                            jobScheduler = jobScheduler,
                            executionControl = executionControl,
                            cancellationToken = token,
                        )
                    }
                activeTodoJobs[todo.id] = processingJob
                processingJob.invokeOnCompletion {
                    activeTodoJobs.remove(todo.id, processingJob)
                    releaseUnstartedTodo(agent, todo.id)
                }
                processingJob.start()
                return true
            } catch (error: Throwable) {
                agentBusySince.remove(agent.id)
                agent.status = AgentStatus.IDLE
                agent.currentTodoId = null
                agent.currentTask = null
                subAgentOps.saveSubAgent(agent)
                todoManager.updateStatus(todo.id, TodoStatus.PENDING)
                throw error
            }
        }

        private fun releaseUnstartedTodo(
            agent: SubAgent,
            todoId: String,
        ) {
            if (agent.currentTodoId == todoId) {
                agentBusySince.remove(agent.id)
                agent.status = AgentStatus.IDLE
                agent.currentTask = null
                agent.currentTodoId = null
                subAgentOps.saveSubAgent(agent)
                subAgentOps.notifyAgent(agent.id, "STATUS:${agent.status.name}")
            }
            workSignal.signal()
        }

        private fun findNextAssignableTodo(requestedTodoId: String? = null): TodoExecutionCandidate? =
            findNextAssignableTodo(
                todoStore
                    .listTodos()
                    .filterNot {
                        decompositionScheduler.isDecomposing(it.id) || shouldDecomposeBeforeExecution(it, requestedTodoId)
                    },
                subAgents,
                requestedTodoId = requestedTodoId,
                isAgentEligible = { agentId ->
                    executionControl?.isExecutionAllowed(agentId) ?: true
                },
            )

        private fun shouldDecomposeBeforeExecution(
            todo: Todo,
            requestedTodoId: String?,
        ): Boolean =
            requestedTodoId == null &&
                taskPlanner.isComplex(todo.description) &&
                !decompositionScheduler.hasAttemptedDecomposition(todo.id)
    }
