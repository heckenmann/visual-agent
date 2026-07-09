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
        if (seed) seedUxTodos()
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
        persistMessage(
            Message(
                role = "sub_agent",
                content = "Cancelled todo $todoId for deleted agent $agentId.",
            ),
        )
    }

    private fun getTodosFromDb(): List<Todo> = todoStore.listTodos()

    private suspend fun pickAndProcessOneTodo() {
        val busyCount = subAgents.values.count { it.status == AgentStatus.BUSY }
        val parallelLimit = parallelism().coerceAtLeast(1)
        if (busyCount >= parallelLimit) return

        val todo = findNextAssignableTodo() ?: return
        val agent = subAgents[todo.assignedAgentId] ?: return
        if (agent.status != AgentStatus.IDLE) return

        agent.status = AgentStatus.BUSY
        agent.currentTodoId = todo.id
        agent.currentTask = todo.description
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

    private fun findNextAssignableTodo(): Todo? {
        val candidates =
            getTodosFromDb()
                .filter { it.status == TodoStatus.PENDING && !it.assignedAgentId.isNullOrBlank() }
                .sortedWith(compareBy({ it.position }, { it.id }))
        return candidates.firstOrNull { subAgents[it.assignedAgentId]?.status == AgentStatus.IDLE }
    }

    private suspend fun processTodoWithLLM(
        agent: SubAgent,
        todoId: String,
        taskDescription: String,
    ) {
        val token = CancellationToken()
        activeCancellationTokens[todoId] = token
        val watcher = startTodoChangeWatcher(todoId, agent.id, taskDescription, token)
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
                        persistCompletionMessage(agent, todoId)
                        return
                    }
                    attempt++
                    if (attempt >= maxRetries) {
                        todoManager.cancelTodo(todoId)
                        persistCancellationMessage(agent, todoId, "Main review rejected final result")
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
                        persistCancellationMessage(agent, todoId, "Failed: ${userError.summary}: ${userError.detail}")
                        return
                    }
                }
            }
        } catch (error: Exception) {
            logger.error(error) { "Autonomous job for todo $todoId crashed unexpectedly" }
            todoManager.cancelTodo(todoId)
            persistCancellationMessage(agent, todoId, "Crashed unexpectedly")
        } finally {
            watcher.close()
            activeCancellationTokens.remove(todoId)
            if (cancelledByChange) {
                handleTodoChangeAfterCancellation(agent, todoId)
            } else {
                agent.status = AgentStatus.IDLE
                agent.currentTask = null
                agent.currentTodoId = null
                saveAgentToDb(agent)
                notifyAgent(agent.id, "STATUS:${agent.status.name}")
            }
        }
    }

    private fun startTodoChangeWatcher(
        todoId: String,
        assignedAgentId: String,
        taskDescription: String,
        token: CancellationToken,
    ): AutoCloseable {
        val handle =
            todoEventBus.addListener { change ->
                if (change.todo?.id != todoId && change.todoId != todoId) return@addListener
                when (change.type) {
                    TodoChangeType.UPDATED -> {
                        val todo = change.todo ?: return@addListener
                        val reassigned = todo.assignedAgentId != assignedAgentId
                        val cancelled = todo.status == TodoStatus.CANCELLED
                        val descriptionChanged = todo.description != taskDescription
                        if (reassigned || cancelled || descriptionChanged) {
                            token.cancel()
                        }
                    }
                    TodoChangeType.REMOVED,
                    TodoChangeType.CLEARED,
                    -> token.cancel()
                    else -> Unit
                }
            }
        return handle
    }

    private fun handleTodoChangeAfterCancellation(
        agent: SubAgent,
        todoId: String,
    ) {
        val change = pendingTodoChanges.remove(todoId)
        val todo = getTodosFromDb().firstOrNull { it.id == todoId }
        when {
            todo == null || todo.status == TodoStatus.CANCELLED || todo.assignedAgentId != agent.id -> {
                todoManager.cancelTodo(todoId)
                persistCancellationMessage(agent, todoId, "Stopped because the todo was cancelled, deleted, or reassigned")
                agent.status = AgentStatus.IDLE
                agent.currentTask = null
                agent.currentTodoId = null
                saveAgentToDb(agent)
                notifyAgent(agent.id, "STATUS:${agent.status.name}")
            }
            change?.todo != null && change.todo.description != agent.currentTask -> {
                agent.currentTask = todo.description
                saveAgentToDb(agent)
                persistMessage(
                    Message(
                        role = "system",
                        content = "Todo $todoId was updated; agent ${agent.id} will continue with the new description.",
                    ),
                )
                scope.launch {
                    jobScheduler.run {
                        processTodoWithLLM(agent, todoId, taskPlanner.buildWorkerInstruction(todo))
                    }
                }
            }
            else -> {
                agent.status = AgentStatus.IDLE
                agent.currentTask = null
                agent.currentTodoId = null
                saveAgentToDb(agent)
                notifyAgent(agent.id, "STATUS:${agent.status.name}")
            }
        }
    }

    private fun persistCompletionMessage(
        agent: SubAgent,
        todoId: String,
    ) {
        persistMessage(
            Message(
                role = "sub_agent",
                content =
                    "Agent ${agent.name} (${agent.id}) completed todo $todoId. " +
                        "Use `todos` with `get-result` to read the stored result.",
            ),
        )
    }

    private fun persistCancellationMessage(
        agent: SubAgent,
        todoId: String,
        reason: String,
    ) {
        persistMessage(
            Message(
                role = "sub_agent",
                content = "Agent ${agent.name} (${agent.id}) stopped todo $todoId. $reason",
            ),
        )
    }

    private companion object {
        const val LOOP_DELAY_MILLIS = 1500L
    }
}
