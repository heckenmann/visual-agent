package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging

/**
 * Coordinates autonomous todo decomposition, assignment, and worker execution loops.
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
    private val createAgent: (name: String, role: String, templateName: String) -> SubAgent,
    private val saveAgentToDb: (SubAgent) -> Unit,
    private val notifyAgent: (agentId: String, message: String) -> Unit,
) {
    private val logger = KotlinLogging.logger {}
    private val taskPlanner =
        AutonomousTaskPlanner(
            todoManager = todoManager,
            subAgents = subAgents,
            llmProvider = llmProvider,
            agentToolConfigService = agentToolConfigService,
            createAgent = createAgent,
        )

    /**
     * Assigns the next pending todo to an idle worker and schedules execution.
     *
     * @return `true` when a todo was assigned
     */
    fun assignNextTodo(): Boolean {
        val idleAgent = taskPlanner.selectWorkerAgentForNextTodo() ?: return false
        val pendingTodo = getTodosFromDb().firstOrNull { it.status == TodoStatus.PENDING } ?: return false
        val assigned = todoManager.assignToAgent(pendingTodo.id, idleAgent.id)
        if (!assigned) return false
        idleAgent.status = AgentStatus.BUSY
        idleAgent.currentTodoId = pendingTodo.id
        idleAgent.currentTask = pendingTodo.description
        saveAgentToDb(idleAgent)
        notifyAgent(idleAgent.id, "STATUS:${idleAgent.status.name}")
        scope.launch {
            jobScheduler.run {
                processTodoWithLLM(idleAgent, pendingTodo.id, taskPlanner.buildWorkerInstruction(pendingTodo))
            }
        }
        return true
    }

    /**
     * Assigns a specific todo to a specific idle agent and schedules execution.
     *
     * @param todoId Todo id
     * @param agentId Agent id
     * @return `true` when assignment succeeded
     */
    fun assignTodoToAgent(
        todoId: String,
        agentId: String,
    ): Boolean {
        val agent = subAgents[agentId] ?: return false
        if (agent.status != AgentStatus.IDLE) return false
        val assigned = todoManager.assignToAgent(todoId, agentId)
        if (!assigned) return false
        val todo = getTodosFromDb().firstOrNull { it.id == todoId } ?: return false
        agent.status = AgentStatus.BUSY
        agent.currentTodoId = todoId
        agent.currentTask = todo.description
        saveAgentToDb(agent)
        notifyAgent(agent.id, "STATUS:${agent.status.name}")
        scope.launch {
            jobScheduler.run {
                processTodoWithLLM(agent, todoId, taskPlanner.buildWorkerInstruction(todo))
            }
        }
        return true
    }

    /**
     * Assigns pending todos up to idle-agent count and configured parallelism limit.
     *
     * @return Number of assignments created
     */
    fun assignAllPendingTodos(): Int {
        val idleCount = subAgents.values.count { it.status == AgentStatus.IDLE }
        val parallelLimit = AppConfig.instance.maxParallelSubAgents.coerceAtLeast(1)
        val assignmentBudget = minOf(idleCount, parallelLimit)
        var count = 0
        repeat(assignmentBudget) {
            if (assignNextTodo()) count++ else return@repeat
        }
        return count
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
     * @param seed Whether standard UX todos should be seeded first
     * @see docs/usecases/uc_0000054_run_autonomous_processing_loop.md
     */
    fun startAutonomousProcessing(seed: Boolean = true) {
        if (seed) seedUxTodos()
        scope.launch {
            while (true) {
                taskPlanner.expandComplexTodoIfNeeded(getTodosFromDb())
                assignAllPendingTodos()
                delay(1500)
                val pending = getTodosFromDb().filter { it.status == TodoStatus.PENDING }
                val inProgress = getTodosFromDb().any { it.status == TodoStatus.IN_PROGRESS }
                val anyAgentBusy = subAgents.values.any { it.status == AgentStatus.BUSY }
                if (pending.isEmpty() && !inProgress && !anyAgentBusy) break
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

    private fun getTodosFromDb(): List<Todo> = todoStore.listTodos()

    private suspend fun processTodoWithLLM(
        agent: SubAgent,
        todoId: String,
        taskDescription: String,
    ) {
        var attempt = 0
        val maxRetries = agent.config.maxRetries
        try {
            delay(300)
            while (attempt < maxRetries) {
                try {
                    val workerResult =
                        agent.performTodo(todoId, taskDescription, llmProvider, memoryStore, agentToolConfigService.toolsFor(agent))
                    val reviewApproved = taskPlanner.reviewWorkerResult(todoId, taskDescription, workerResult)
                    if (reviewApproved) {
                        notifyAgent(agent.id, "Completed todo: $todoId")
                        todoManager.completeTodo(todoId)
                        break
                    }
                    attempt++
                    if (attempt >= maxRetries) {
                        notifyAgent(agent.id, "Main review rejected final result for todo: $todoId")
                        todoManager.cancelTodo(todoId)
                        break
                    }
                    notifyAgent(agent.id, "Main review requested retry for todo: $todoId")
                } catch (error: Exception) {
                    attempt++
                    val backoff = 500L * attempt
                    logger.warn(error) { "Autonomous todo $todoId failed on attempt $attempt for agent ${agent.id}" }
                    delay(backoff)
                    if (attempt >= maxRetries) {
                        val userError = ErrorMessageMapper.map(error)
                        todoManager.cancelTodo(todoId)
                        notifyAgent(agent.id, "Failed todo: $todoId — ${userError.summary}: ${userError.detail}")
                    }
                }
            }
        } catch (error: Exception) {
            logger.error(error) { "Autonomous job for todo $todoId crashed unexpectedly" }
            todoManager.cancelTodo(todoId)
        } finally {
            agent.status = AgentStatus.IDLE
            agent.currentTask = null
            agent.currentTodoId = null
            saveAgentToDb(agent)
            notifyAgent(agent.id, "STATUS:${agent.status.name}")
        }
    }
}
