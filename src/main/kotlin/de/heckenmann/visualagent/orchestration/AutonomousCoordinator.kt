package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates autonomous todo decomposition and worker execution through a continuous
 * auto-pickup loop.
 *
 * Use cases: UC-0000014, UC-0000053, UC-0000054, UC-0000055, UC-0000057.
 */
internal class AutonomousCoordinator(
    private val scope: CoroutineScope,
    private val todoManager: TodoManager,
    private val subAgents: MutableMap<String, SubAgent>,
    private val llmProvider: LLMProvider,
    private val todoStore: TodoStore,
    private val memoryStore: MemoryStore,
    private val agentToolConfigService: AgentToolConfigService,
    private val jobScheduler: SubAgentJobScheduler,
    private val parallelism: () -> Int,
    private val todoEventBus: TodoEventBus,
    private val createAgent: (name: String, role: String, templateName: String) -> SubAgent,
    private val saveAgentToDb: (SubAgent) -> Unit,
    private val notifyAgent: (agentId: String, message: String) -> Unit,
    private val persistMessage: (Message) -> Unit,
) {
    private val logger = KotlinLogging.logger {}
    private val pendingTodoChanges = ConcurrentHashMap<String, TodoChange>()
    private val activeCancellationTokens = ConcurrentHashMap<String, de.heckenmann.visualagent.agent.CancellationToken>()
    private val agentBusySince = ConcurrentHashMap<String, Long>()
    private var loopJob: kotlinx.coroutines.Job? = null
    private var loopStarted = false
    private val taskPlanner =
        AutonomousTaskPlanner(
            todoManager = todoManager,
            subAgents = subAgents,
            llmProvider = llmProvider,
            agentToolConfigService = agentToolConfigService,
            createAgent = createAgent,
        )

    init {
        logger.info { "AutonomousCoordinator initialized with ${subAgents.size} agents" }
        todoEventBus.addListener { change ->
            change.todo?.id?.let { pendingTodoChanges[it] = change }
            change.todoId?.let { pendingTodoChanges[it] = change }
            val todo = change.todo
            if (todo?.status == TodoStatus.PENDING) {
                activeCancellationTokens[todo.id]?.cancel()
            }
            val todoBecamePending =
                todo?.status == TodoStatus.PENDING &&
                    (
                        change.type == TodoChangeType.ADDED ||
                            change.type == TodoChangeType.UPDATED
                    )
            if (todoBecamePending) {
                restartLoopIfStopped()
            }
        }
    }

    private fun restartLoopIfStopped() {
        if (loopStarted && loopJob?.isActive != true) {
            startAutonomousProcessing(seed = false)
        }
    }

    /**
     * Seeds standard UX-focused todos used for autonomous improvement loops.
     *
     * Missing tasks are added; existing tasks with the same description are skipped
     * so repeated seeding does not create duplicates.
     *
     * Use cases: UC-0000053.
     */
    fun seedUxTodos() {
        val existingDescriptions = getTodosFromDb().mapTo(mutableSetOf()) { it.description }
        UxSeedTasks
            .all()
            .filterNot { it in existingDescriptions }
            .forEach { desc -> todoManager.add(desc) }
    }

    /**
     * Starts autonomous processing loop.
     *
     * The loop continuously picks the first pending todo by position whose assigned
     * sub-agent is idle, marks it in-progress, executes it, and persists a
     * conversation message with the result. It exits when no pending or in-progress
     * work remains and no agents are busy.
     *
     * @param seed Whether standard UX todos should be seeded first
     * @see docs/usecases/uc_0000054_run_autonomous_processing_loop.md
     */
    fun startAutonomousProcessing(seed: Boolean = true) {
        loopStarted = true
        if (loopJob?.isActive == true) return
        if (seed) seedUxTodos()
        loopJob =
            scope.launch {
                while (true) {
                    taskPlanner.expandComplexTodoIfNeeded(getTodosFromDb())
                    pickAndProcessOneTodo()
                    val pending = getTodosFromDb().filter { it.status == TodoStatus.PENDING }
                    val inProgress = getTodosFromDb().any { it.status == TodoStatus.IN_PROGRESS }
                    val anyAgentBusy = subAgents.values.any { it.status == AgentStatus.BUSY }
                    if (pending.isEmpty() && !inProgress && !anyAgentBusy) break
                    delay(LOOP_DELAY_MILLIS)
                }
            }
    }

    /**
     * Enqueues one goal and starts autonomous processing without seeding defaults.
     *
     * @param goal Objective text
     * @see docs/usecases/uc_0000055_start_autonomous_goal.md
     */
    fun startAutonomousMode(goal: String) {
        if (goal.isNotBlank()) todoManager.add(goal.trim())
        startAutonomousProcessing(seed = false)
    }

    /**
     * Cancels the in-progress todo assigned to the given agent and persists a notification.
     *
     * @param agentId Sub-agent whose current todo should be cancelled
     */
    fun cancelAgentTodo(agentId: String) {
        val agent = subAgents[agentId] ?: return
        val todoId = agent.currentTodoId ?: return
        val todo = getTodosFromDb().firstOrNull { it.id == todoId } ?: return
        if (todo.status != TodoStatus.IN_PROGRESS) return
        todoManager.cancelTodo(todoId)
        persistSubAgentMessage(
            agent = agent,
            content = "Cancelled todo $todoId for deleted agent $agentId.",
            success = false,
            persistMessage = persistMessage,
        )
    }

    private fun getTodosFromDb(): List<Todo> = todoStore.listTodos()

    private suspend fun pickAndProcessOneTodo() {
        val busyCount = subAgents.values.count { it.status == AgentStatus.BUSY }
        val parallelLimit = parallelism().coerceAtLeast(1)
        if (busyCount >= parallelLimit) return

        val todo = findNextAssignableTodo() ?: return
        val agent = subAgents[todo.assignedAgentId] ?: return
        if (agent.status == AgentStatus.BUSY) {
            if (recoverStuckAgentIfNeeded(agent)) {
                todoManager.updateStatus(todo.id, TodoStatus.PENDING)
            }
            return
        }
        if (agent.status != AgentStatus.IDLE) return

        agent.status = AgentStatus.BUSY
        agent.currentTodoId = todo.id
        agent.currentTask = todo.description
        agentBusySince[agent.id] = System.currentTimeMillis()
        saveAgentToDb(agent)
        todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)
        persistMessage(
            Message(
                role = "system",
                content = "Started todo ${todo.id} (${todo.description.take(80)}) with agent ${agent.id} (${agent.name}).",
            ),
        )
        notifyAgent(agent.id, "STATUS:${agent.status.name}")

        scope.launch {
            jobScheduler.run {
                processTodoWithLLM(agent, todo.id, taskPlanner.buildWorkerInstruction(todo))
            }
        }
    }

    private fun recoverStuckAgentIfNeeded(agent: SubAgent): Boolean {
        val busySince = agentBusySince[agent.id] ?: return false
        val timeoutMs = agent.config.timeout.coerceAtLeast(1) * 1000L
        if (System.currentTimeMillis() - busySince < timeoutMs) return false
        val todoId = agent.currentTodoId ?: return false
        activeCancellationTokens[todoId]?.cancel()
        agentBusySince.remove(agent.id)
        agent.status = AgentStatus.IDLE
        agent.currentTask = null
        agent.currentTodoId = null
        saveAgentToDb(agent)
        todoManager.cancelTodo(todoId)
        persistMessage(
            Message(
                role = "sub_agent",
                content =
                    "Agent ${agent.name} (${agent.id}) stopped todo $todoId " +
                        "because it exceeded the configured timeout of ${agent.config.timeout}s.",
            ),
        )
        notifyAgent(agent.id, "STATUS:${agent.status.name}")
        return true
    }

    private fun findNextAssignableTodo(): Todo? = findNextAssignableTodo(getTodosFromDb(), subAgents, todoManager)

    private suspend fun processTodoWithLLM(
        agent: SubAgent,
        todoId: String,
        taskDescription: String,
    ) {
        val token = CancellationToken()
        activeCancellationTokens[todoId] = token
        val watcher = startTodoChangeWatcher(todoId, agent.id, taskDescription, token, todoEventBus)
        var attempt = 0
        val maxRetries = agent.config.maxRetries.coerceAtLeast(1)
        var instruction = taskDescription
        var cancelledByChange = false
        try {
            delay(300)
            while (attempt < maxRetries) {
                try {
                    val result =
                        agent.performTodo(
                            todoId,
                            instruction,
                            llmProvider,
                            memoryStore,
                            agentToolConfigService.toolsFor(agent),
                            token,
                        )
                    val reviewApproved = taskPlanner.reviewWorkerResult(todoId, instruction, result)
                    if (reviewApproved) {
                        todoManager.completeTodo(todoId)
                        persistSubAgentMessage(
                            agent = agent,
                            content =
                                "Agent ${agent.name} (${agent.id}) completed todo $todoId. " +
                                    "Use `todos` with `get-result` to read the stored result.",
                            success = true,
                            persistMessage = persistMessage,
                        )
                        return
                    }
                    attempt++
                    if (attempt >= maxRetries) {
                        todoManager.cancelTodo(todoId)
                        persistSubAgentMessage(
                            agent = agent,
                            content =
                                "Agent ${agent.name} (${agent.id}) stopped todo $todoId. " +
                                    "Main review rejected final result",
                            success = false,
                            persistMessage = persistMessage,
                        )
                        return
                    }
                    notifyAgent(agent.id, "Main review requested retry for todo: $todoId")
                } catch (error: kotlinx.coroutines.CancellationException) {
                    cancelledByChange = true
                    break
                } catch (error: Exception) {
                    attempt++
                    val backoff = 500L * attempt
                    logger.warn(error) { "Autonomous todo $todoId failed on attempt $attempt for agent ${agent.id}" }
                    delay(backoff)
                    if (attempt >= maxRetries) {
                        val userError = ErrorMessageMapper.map(error)
                        todoManager.cancelTodo(todoId)
                        persistSubAgentMessage(
                            agent = agent,
                            content =
                                "Agent ${agent.name} (${agent.id}) stopped todo $todoId. " +
                                    "Failed: ${userError.summary}: ${userError.detail}",
                            success = false,
                            persistMessage = persistMessage,
                        )
                        return
                    }
                }
            }
        } catch (error: Exception) {
            logger.error(error) { "Autonomous job for todo $todoId crashed unexpectedly" }
            todoManager.cancelTodo(todoId)
            persistSubAgentMessage(
                agent = agent,
                content = "Agent ${agent.name} (${agent.id}) stopped todo $todoId. Crashed unexpectedly",
                success = false,
                persistMessage = persistMessage,
            )
        } finally {
            watcher.close()
            activeCancellationTokens.remove(todoId)
            agentBusySince.remove(agent.id)
            if (cancelledByChange) {
                handleTodoChangeAfterCancellation(
                    agent = agent,
                    todoId = todoId,
                    pendingTodoChanges = pendingTodoChanges,
                    currentTodo = getTodosFromDb().firstOrNull { it.id == todoId },
                    todoManager = todoManager,
                    persistMessage = persistMessage,
                    saveAgentToDb = saveAgentToDb,
                    notifyAgent = notifyAgent,
                    onDescriptionChanged = { changedAgent, todo ->
                        scope.launch {
                            jobScheduler.run {
                                processTodoWithLLM(changedAgent, todo.id, taskPlanner.buildWorkerInstruction(todo))
                            }
                        }
                    },
                )
            } else {
                agent.status = AgentStatus.IDLE
                agent.currentTask = null
                agent.currentTodoId = null
                saveAgentToDb(agent)
                notifyAgent(agent.id, "STATUS:${agent.status.name}")
            }
        }
    }

    private companion object {
        const val LOOP_DELAY_MILLIS = 1500L
    }
}
