package de.heckenmann.visualagent.agent.autonomy

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.AgentToolConfigService
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates autonomous todo decomposition, assignment, and worker execution loops.
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
    /**
     * Assigns the next pending todo to an idle worker and schedules execution.
     *
     * @return `true` when a todo was assigned
     */
    fun assignNextTodo(): Boolean {
        val idleAgent = selectWorkerAgentForNextTodo() ?: return false
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
                processTodoWithLLM(idleAgent, pendingTodo.id, buildWorkerInstruction(pendingTodo))
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
                processTodoWithLLM(agent, todoId, buildWorkerInstruction(todo))
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
     */
    fun seedUxTodos() {
        val tasks = UxSeedTasks.all()
        tasks.forEach { desc -> todoManager.add(desc) }
    }

    /**
     * Starts autonomous processing loop.
     *
     * @param seed Whether standard UX todos should be seeded first
     */
    fun startAutonomousProcessing(seed: Boolean = true) {
        if (seed) seedUxTodos()
        scope.launch {
            while (true) {
                expandComplexTodoIfNeeded()
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
                    val reviewApproved = reviewWorkerResult(todoId, taskDescription, workerResult)
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
                } catch (_: Exception) {
                    attempt++
                    val backoff = 500L * attempt
                    delay(backoff)
                    if (attempt >= maxRetries) {
                        todoManager.cancelTodo(todoId)
                    }
                }
            }
        } catch (_: Exception) {
            todoManager.cancelTodo(todoId)
        } finally {
            agent.status = AgentStatus.IDLE
            agent.currentTask = null
            agent.currentTodoId = null
            saveAgentToDb(agent)
            notifyAgent(agent.id, "STATUS:${agent.status.name}")
        }
    }

    private suspend fun expandComplexTodoIfNeeded(): Boolean {
        val candidate = getTodosFromDb().firstOrNull { it.status == TodoStatus.PENDING && isComplex(it.description) } ?: return false
        val analyst = ensureAnalysisAgent()
        val prompt =
            listOf(
                Message("system", "You are an analysis agent. Break down complex tasks into 2-6 concise actionable subtasks."),
                Message("user", "Task: ${candidate.description}\nReturn each subtask on its own line without numbering."),
            )
        val response = analyst.chat(prompt, llmProvider, agentToolConfigService.toolsFor(analyst)).message.content
        val subtasks =
            response
                .lines()
                .map { it.trim().trimStart('-', '*', '•').trim() }
                .filter { it.isNotBlank() && it.length > 3 }
                .distinct()
                .take(8)
        if (subtasks.isEmpty()) return false
        todoManager.cancelTodo(candidate.id)
        subtasks.forEach { sub -> todoManager.add(sub) }
        return true
    }

    private fun isComplex(description: String): Boolean {
        val words = description.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 16) return true
        val lower = description.lowercase()
        val complexityHints = listOf("and", "then", "integrate", "architecture", "pipeline", "multi", "parallel")
        return complexityHints.any { lower.contains(it) }
    }

    private fun ensureAnalysisAgent(): SubAgent =
        subAgents.values.firstOrNull { it.name.contains("analyst", true) || it.role.contains("analysis", true) }
            ?: createAgent("Analyst", "Task decomposition and structured planning", "researcher")

    private fun selectWorkerAgentForNextTodo(): SubAgent? {
        val pending = todoManager.getPending().firstOrNull() ?: return null
        val idleAgents = subAgents.values.filter { it.status == AgentStatus.IDLE }
        if (idleAgents.isEmpty()) return null
        val preferred =
            idleAgents.firstOrNull {
                val description = pending.description.lowercase()
                (description.contains("code") || description.contains("implement") || description.contains("fix")) &&
                    it.name.contains("coder", true) ||
                    (description.contains("research") || description.contains("docs") || description.contains("issue")) &&
                    it.name.contains("research", true)
            }
        return preferred ?: idleAgents.firstOrNull() ?: createDynamicWorkerFor(pending.description)
    }

    private fun createDynamicWorkerFor(description: String): SubAgent {
        val lower = description.lowercase()
        return when {
            lower.contains("test") -> createAgent("Tester", "Focused test implementation and validation", "tester")
            lower.contains("review") -> createAgent("Reviewer", "Code and architecture review", "reviewer")
            lower.contains("doc") -> createAgent("Documenter", "Documentation and developer guides", "documenter")
            else -> createAgent("Worker-${java.util.UUID.randomUUID().toString().take(4)}", "General execution worker", "coder")
        }
    }

    private fun buildWorkerInstruction(todo: Todo): String {
        val localHints =
            """
            Prioritize local project context first:
            - read project markdown docs
            - inspect code comments and relevant source files
            If blocked after multiple attempts:
            - gather external references
            - prioritize GitHub issues, StackOverflow, and search engines
            """.trimIndent()
        return """
            Task ID: ${todo.id}
            Priority: ${todo.priority}
            Objective: ${todo.description}
            
            Deliverable requirements:
            1. Provide concrete implementation steps and results.
            2. Explain which files or components were analyzed.
            3. If blocked, include attempted steps and next actions.
            
            $localHints
            """.trimIndent()
    }

    private suspend fun reviewWorkerResult(
        todoId: String,
        taskDescription: String,
        workerResult: String,
    ): Boolean {
        val reviewPrompt =
            listOf(
                Message(
                    "system",
                    "You are the main reviewer. Respond with exactly APPROVED or RETRY in first line, then a short rationale.",
                ),
                Message(
                    "user",
                    """
                    TODO ID: $todoId
                    Task: $taskDescription
                    Worker Result:
                    $workerResult
                    """.trimIndent(),
                ),
            )
        val response =
            llmProvider.chat(
                de.heckenmann.visualagent.agent.ChatRequestContext(
                    messages = reviewPrompt,
                    enabledTools = agentToolConfigService.mainAgentTools(),
                    metadata = mapOf("sessionId" to "main", "agent" to "main"),
                ),
            )
        return response.message.content
            .trim()
            .uppercase()
            .startsWith("APPROVED")
    }
}
