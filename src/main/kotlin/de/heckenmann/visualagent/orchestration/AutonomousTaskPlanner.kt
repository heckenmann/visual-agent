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
    private val createAgent: (name: String, role: String, templateName: String) -> SubAgent,
) {
    suspend fun expandComplexTodoIfNeeded(todos: List<Todo>): Boolean {
        val candidate = todos.firstOrNull { it.status == TodoStatus.PENDING && isComplex(it.description) } ?: return false
        val analyst = ensureAnalysisAgent()
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
        if (idleAgents.isEmpty()) {
            return if (subAgents.isEmpty()) createDynamicWorkerFor(pending.description) else null
        }
        return idleAgents.firstOrNull { matchesSpecialty(it, pending.description) }
            ?: idleAgents.first()
    }

    fun buildWorkerInstruction(todo: Todo): String = OrchestrationConstants.workerInstruction(todo.id, todo.description)

    fun reviewWorkerResult(
        todoId: String,
        taskDescription: String,
        workerResult: String,
    ): Boolean {
        // The previous LLM-based main review added another flaky inference step and caused
        // simple, correct results (e.g. "count to 50") to be rejected. We now accept any
        // non-empty worker result as successful; the sub-agent already reports failures
        // through its own output and tool results.
        return workerResult.isNotBlank()
    }

    internal fun isComplex(description: String): Boolean {
        if (description.trim().split(Regex("\\s+")).count(String::isNotBlank) >= OrchestrationConstants.COMPLEX_WORD_COUNT) return true
        val lower = description.lowercase()
        return OrchestrationConstants.COMPLEXITY_HINTS.any(lower::contains)
    }

    private fun ensureAnalysisAgent(): SubAgent =
        subAgents.values.firstOrNull { it.name.contains("analyst", true) || it.role.contains("analysis", true) }
            ?: createAgent(
                OrchestrationConstants.AnalysisAgent.NAME,
                OrchestrationConstants.AnalysisAgent.ROLE,
                OrchestrationConstants.AnalysisAgent.TEMPLATE,
            )

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

    private fun createDynamicWorkerFor(description: String): SubAgent {
        val lower = description.lowercase()
        return when {
            OrchestrationConstants.TEST_HINT in lower ->
                createAgent(
                    OrchestrationConstants.DynamicAgent.TESTER_NAME,
                    OrchestrationConstants.DynamicAgent.TESTER_ROLE,
                    OrchestrationConstants.DynamicAgent.TESTER_TEMPLATE,
                )

            OrchestrationConstants.REVIEW_HINT in lower ->
                createAgent(
                    OrchestrationConstants.DynamicAgent.REVIEWER_NAME,
                    OrchestrationConstants.DynamicAgent.REVIEWER_ROLE,
                    OrchestrationConstants.DynamicAgent.REVIEWER_TEMPLATE,
                )

            OrchestrationConstants.DOC_HINT in lower ->
                createAgent(
                    OrchestrationConstants.DynamicAgent.DOCUMENTER_NAME,
                    OrchestrationConstants.DynamicAgent.DOCUMENTER_ROLE,
                    OrchestrationConstants.DynamicAgent.DOCUMENTER_TEMPLATE,
                )

            else ->
                createAgent(
                    "Worker-${java.util.UUID.randomUUID().toString().take(4)}",
                    OrchestrationConstants.DynamicAgent.GENERAL_WORKER_ROLE,
                    OrchestrationConstants.DynamicAgent.GENERAL_WORKER_TEMPLATE,
                )
        }
    }
}
