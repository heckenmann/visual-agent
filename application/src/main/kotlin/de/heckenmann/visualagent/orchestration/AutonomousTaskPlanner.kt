package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.replaceWithChildrenReactive
import reactor.core.publisher.Mono

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
    fun expandComplexTodoIfNeeded(todos: List<Todo>): Mono<Boolean> {
        val candidate = todos.firstOrNull { it.status == TodoStatus.PENDING && isComplex(it.description) } ?: return Mono.just(false)
        return expandComplexTodo(candidate)
    }

    fun expandComplexTodo(
        candidate: Todo,
        analyst: SubAgent? = analysisAgent(),
    ): Mono<Boolean> =
        Mono.defer {
            if (candidate.status != TodoStatus.PENDING ||
                candidate.decompositionDepth > 0 ||
                !isComplex(candidate.description)
            ) {
                return@defer Mono.just(false)
            }
            analyst ?: return@defer Mono.just(false)
            val prompt = OrchestrationConstants.decompositionPrompt(candidate.description)
            analyst.chatReactive(prompt, llmProvider, agentToolConfigService.toolsFor(analyst)).flatMap { reply ->
                val response = reply.message.content
                val subtasks =
                    response
                        .lineSequence()
                        .map { it.trim().trimStart(*OrchestrationConstants.SUBTASK_PREFIX_CHARS).trim() }
                        .filter { it.length > OrchestrationConstants.MIN_SUBTASK_LENGTH }
                        .distinct()
                        .take(OrchestrationConstants.MAX_SUBTASKS)
                        .toList()
                if (subtasks.isEmpty() ||
                    subAgents[analyst.id] !== analyst ||
                    analyst.status == AgentStatus.OFFLINE
                ) {
                    return@flatMap Mono.just(false)
                }
                val normalized = { text: String -> text.trim().replace(Regex("\\s+"), " ").lowercase() }
                if (subtasks.size == 1 &&
                    normalized(subtasks.single()) == normalized(candidate.description)
                ) {
                    return@flatMap Mono.just(false)
                }
                todoManager.replaceWithChildrenReactive(candidate, subtasks)
            }
        }

    fun selectWorkerAgentForNextTodo(): SubAgent? {
        val pending = todoManager.getPending().firstOrNull() ?: return null
        val idleAgents = subAgents.values.filter { it.status == AgentStatus.IDLE }
        if (idleAgents.isEmpty()) return null
        return idleAgents.firstOrNull { matchesSpecialty(it, pending.description) }
            ?: idleAgents.first()
    }

    fun buildWorkerInstruction(todo: Todo): String = OrchestrationConstants.workerInstruction(todo.id, todo.description)

    fun reviewWorkerResult(
        todoId: String,
        taskDescription: String,
        workerResult: String,
        cancellationToken: CancellationToken? = null,
        executionEvidence: String = "No tool execution evidence was recorded for this attempt.",
    ): Mono<WorkerReviewResult> =
        evaluateWorkerResult(llmProvider, todoId, taskDescription, workerResult, cancellationToken, executionEvidence)

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
