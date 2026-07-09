package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
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
    private val subAgents: MutableMap<String, SubAgent>,
    private val llmProvider: LLMProvider,
    private val agentToolConfigService: AgentToolConfigService,
    private val createAgent: (name: String, role: String, templateName: String) -> SubAgent,
) {
    suspend fun expandComplexTodoIfNeeded(todos: List<Todo>): Boolean {
        val candidate = todos.firstOrNull { it.status == TodoStatus.PENDING && isComplex(it.description) } ?: return false
        val analyst = ensureAnalysisAgent()
        val prompt =
            listOf(
                Message("system", "You are an analysis agent. Break down complex tasks into 2-6 concise actionable subtasks."),
                Message("user", "Task: ${candidate.description}\nReturn each subtask on its own line without numbering."),
            )
        val response = analyst.chat(prompt, llmProvider, agentToolConfigService.toolsFor(analyst)).message.content
        val subtasks =
            response
                .lineSequence()
                .map { it.trim().trimStart('-', '*', '•').trim() }
                .filter { it.length > 3 }
                .distinct()
                .take(8)
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

    fun buildWorkerInstruction(todo: Todo): String =
        """
        Task ID: ${todo.id}
        Objective: ${todo.description}

        This is the next pending todo by list order. Treat it as the highest priority work item.

        Deliverable requirements:
        1. Provide concrete implementation steps and results.
        2. Explain which files or components were analyzed.
        3. If blocked, include attempted steps and next actions.

        Prioritize local project context first. Inspect project documentation, code comments, and relevant source files.
        If blocked after multiple attempts, gather external references from authoritative sources.
        """.trimIndent()

    suspend fun reviewWorkerResult(
        todoId: String,
        taskDescription: String,
        workerResult: String,
    ): Boolean {
        val prompt =
            listOf(
                Message("system", "Respond with exactly APPROVED or RETRY in the first line, then a short rationale."),
                Message("user", "TODO ID: $todoId\nTask: $taskDescription\nWorker Result:\n$workerResult"),
            )
        val response =
            llmProvider.chat(
                ChatRequestContext(
                    messages = prompt,
                    enabledTools = agentToolConfigService.mainAgentTools(),
                    metadata = mapOf("sessionId" to "main", "agent" to "main"),
                ),
            )
        return response.message.content
            .trim()
            .uppercase()
            .startsWith("APPROVED")
    }

    internal fun isComplex(description: String): Boolean {
        if (description.trim().split(Regex("\\s+")).count(String::isNotBlank) >= 16) return true
        val lower = description.lowercase()
        return COMPLEXITY_HINTS.any(lower::contains)
    }

    private fun ensureAnalysisAgent(): SubAgent =
        subAgents.values.firstOrNull { it.name.contains("analyst", true) || it.role.contains("analysis", true) }
            ?: createAgent("Analyst", "Task decomposition and structured planning", "researcher")

    private fun matchesSpecialty(
        agent: SubAgent,
        description: String,
    ): Boolean {
        val lower = description.lowercase()
        val codeTask = CODE_HINTS.any(lower::contains)
        val researchTask = RESEARCH_HINTS.any(lower::contains)
        return codeTask &&
            agent.name.contains("coder", true) ||
            researchTask &&
            agent.name.contains("research", true)
    }

    private fun createDynamicWorkerFor(description: String): SubAgent {
        val lower = description.lowercase()
        return when {
            "test" in lower -> createAgent("Tester", "Focused test implementation and validation", "tester")
            "review" in lower -> createAgent("Reviewer", "Code and architecture review", "reviewer")
            "doc" in lower -> createAgent("Documenter", "Documentation and developer guides", "documenter")
            else -> createAgent("Worker-${java.util.UUID.randomUUID().toString().take(4)}", "General execution worker", "coder")
        }
    }

    private companion object {
        val COMPLEXITY_HINTS = listOf("and", "then", "integrate", "architecture", "pipeline", "multi", "parallel")
        val CODE_HINTS = listOf("code", "implement", "fix")
        val RESEARCH_HINTS = listOf("research", "docs", "issue")
    }
}
