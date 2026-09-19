package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus

/**
 * Decomposes complex todos, selects suitable workers, and reviews worker output.
 *
 * Use cases: UC-0000056, UC-0000057.
 */
internal class AutonomousTaskPlanner(
    private val todoManager: TodoManager,
    private val subAgents: Map<String, SubAgent>,
    private val llmProvider: LLMProvider,
    private val agentToolConfigService: AgentToolConfigService,
) {
    suspend fun expandComplexTodoIfNeeded(todos: List<Todo>): Boolean {
        val candidate = todos.firstOrNull { it.status == TodoStatus.PENDING && isComplex(it.description) } ?: return false
        return expandComplexTodo(candidate)
    }

    suspend fun expandComplexTodo(
        candidate: Todo,
        analyst: SubAgent? = analysisAgent(),
    ): Boolean {
        if (candidate.status != TodoStatus.PENDING || !isComplex(candidate.description)) return false
        analyst ?: return false
        val prompt = OrchestrationConstants.decompositionPrompt(candidate.description)
        val response = analyst.chat(prompt, llmProvider, agentToolConfigService.toolsFor(analyst)).message.content
        val subtasks =
            response
                .lineSequence()
                .map { it.trim().trimStart(*OrchestrationConstants.SUBTASK_PREFIX_CHARS).trim() }
                .filter { it.length > OrchestrationConstants.MIN_SUBTASK_LENGTH }
                .distinct()
                .take(OrchestrationConstants.MAX_SUBTASKS)
                .toList()
        if (subtasks.isEmpty()) return false
        todoManager.cancelTodo(candidate.id)
        subtasks.forEach(todoManager::add)
        return true
    }

    fun selectWorkerAgentForNextTodo(): SubAgent? {
        val pending = todoManager.getPending().firstOrNull() ?: return null
        val idleAgents = subAgents.values.filter { it.status == AgentStatus.IDLE }
        if (idleAgents.isEmpty()) return null
        return idleAgents.firstOrNull { matchesSpecialty(it, pending.description) }
            ?: idleAgents.first()
    }

    fun buildWorkerInstruction(todo: Todo): String = OrchestrationConstants.workerInstruction(todo.id, todo.description)

    suspend fun reviewWorkerResult(
        todoId: String,
        taskDescription: String,
        workerResult: String,
        systemPrompt: String,
    ): Boolean {
        val prompt = OrchestrationConstants.reviewPrompt(taskDescription, workerResult, systemPrompt)
        val request =
            de.heckenmann.visualagent.agent.ChatRequestContext(
                messages = prompt,
                enabledTools = emptySet(),
                metadata = mapOf("sessionId" to "review", "todoId" to todoId),
            )
        val response = llmProvider.chat(request)
        val verdict =
            response
                .message
                .content
                .trim()
                .uppercase()
        return verdict.startsWith("APPROVED")
    }

    internal fun isComplex(description: String): Boolean {
        if (description.trim().split(Regex("\\s+")).count(String::isNotBlank) >= OrchestrationConstants.COMPLEX_WORD_COUNT) return true
        val lower = description.lowercase()
        return OrchestrationConstants.COMPLEXITY_HINTS.any(lower::contains)
    }

    /** Returns a persisted analysis agent, or null when the main model has not created one. */
    internal fun analysisAgent(): SubAgent? =
        subAgents.values.firstOrNull { it.name.contains("analyst", true) || it.role.contains("analysis", true) }

    private fun matchesSpecialty(
        agent: SubAgent,
        description: String,
    ): Boolean {
        val lower = description.lowercase()
        val codeTask = OrchestrationConstants.CODE_HINTS.any(lower::contains)
        val researchTask = OrchestrationConstants.RESEARCH_HINTS.any(lower::contains)
        return codeTask &&
            agent.name.contains("coder", true) ||
            researchTask &&
            agent.name.contains("research", true)
    }
}
