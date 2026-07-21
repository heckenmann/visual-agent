package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ParallelismProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
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
    ) {
        private val logger = KotlinLogging.logger {}
        private val subAgents: Map<String, SubAgent>
            get() = subAgentOps.allSubAgents
        private val pendingTodoChanges = ConcurrentHashMap<String, TodoChange>()
        private val activeCancellationTokens = ConcurrentHashMap<String, CancellationToken>()
        private val agentBusySince = ConcurrentHashMap<String, Long>()
        private var loopJob: kotlinx.coroutines.Job? = null
        private var loopStarted = false
        private val taskPlanner =
            AutonomousTaskPlanner(
                todoManager = todoManager,
                subAgents = subAgents,
                llmProvider = llmProvider,
                agentToolConfigService = agentToolConfigService,
                createAgent = { name, role, templateName -> subAgentOps.createAgent(name, role, templateName) },
            )

        init {
            logger.info { "AutonomousCoordinator initialized" }
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
         * Seeds the default UX improvement todos if they do not already exist in the database.
         */
        fun seedUxTodos() {
            val existingDescriptions = getTodosFromDb().mapTo(mutableSetOf()) { it.description }
            UxSeedTasks.all().filterNot { it in existingDescriptions }.forEach { desc -> todoManager.add(desc) }
        }

        /**
         * Starts the autonomous todo-processing loop. Optionally seeds UX todos first.
         * The loop picks pending todos, assigns them to sub-agents, and processes them until
         * no work remains.
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
         * Starts autonomous mode with a specific goal, adding it as a todo and beginning
         * the processing loop without seeding UX todos.
         */
        fun startAutonomousMode(goal: String) {
            if (goal.isNotBlank()) todoManager.add(goal.trim())
            startAutonomousProcessing(seed = false)
        }

        /**
         * Cancels the in-progress todo assigned to the given agent.
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
                persistMessage = { conversationOps.persist(it) },
            )
        }

        private fun getTodosFromDb(): List<Todo> = todoStore.listTodos()

        private suspend fun pickAndProcessOneTodo() {
            val busyCount = subAgents.values.count { it.status == AgentStatus.BUSY }
            val parallelLimit = parallelismProvider.get().coerceAtLeast(1)
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
            subAgentOps.saveSubAgent(agent)
            todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)
            conversationOps.persist(
                Message(
                    role = "system",
                    content = "Started todo ${todo.id} (${todo.description.take(80)}) with agent ${agent.id} (${agent.name}).",
                ),
            )
            subAgentOps.notifyAgent(agent.id, "STATUS:${agent.status.name}")

            scope.launch {
                jobScheduler.run {
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
                    )
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
            subAgentOps.saveSubAgent(agent)
            todoManager.cancelTodo(todoId)
            conversationOps.persist(
                Message(
                    role = "sub_agent",
                    content =
                        "Agent ${agent.name} (${agent.id}) stopped todo $todoId " +
                            "because it exceeded the configured timeout of ${agent.config.timeout}s.",
                ),
            )
            subAgentOps.notifyAgent(agent.id, "STATUS:${agent.status.name}")
            return true
        }

        private fun findNextAssignableTodo(): Todo? = findNextAssignableTodo(getTodosFromDb(), subAgents, todoManager)

        private companion object {
            const val LOOP_DELAY_MILLIS = 1500L
        }
    }
