package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus

/**
 * A pending todo and compatible idle agent selected for an atomic execution claim.
 */
internal data class TodoExecutionCandidate(
    val todo: Todo,
    val agent: SubAgent,
)

/**
 * Selects the next pending todo that can be claimed by an idle agent.
 *
 * @param todos Current todo list
 * @param subAgents Available sub-agents keyed by id
 * @param requestedTodoId Optional todo id to restrict selection to one explicit request
 * @param isAgentEligible Gate checked before an assigned or auto-selected agent is used
 * @return The next todo and agent ready for an atomic claim, or null if none is available
 */
internal fun findNextAssignableTodo(
    todos: List<Todo>,
    subAgents: Map<String, SubAgent>,
    requestedTodoId: String? = null,
    isAgentEligible: (String) -> Boolean = { true },
): TodoExecutionCandidate? =
    todos
        .filter { it.status == TodoStatus.PENDING && (requestedTodoId == null || it.id == requestedTodoId) }
        .sortedWith(compareBy({ it.position }, { it.id }))
        .firstNotNullOfOrNull { findCompatibleAgent(it, subAgents, isAgentEligible)?.let { agent -> TodoExecutionCandidate(it, agent) } }

private fun findCompatibleAgent(
    todo: Todo,
    subAgents: Map<String, SubAgent>,
    isAgentEligible: (String) -> Boolean,
): SubAgent? =
    when {
        !todo.assignedAgentId.isNullOrBlank() ->
            subAgents[todo.assignedAgentId]?.takeIf {
                val agentId = todo.assignedAgentId ?: return@takeIf false
                isAgentEligible(agentId) && it.status == AgentStatus.IDLE
            }
        else ->
            subAgents.values.firstOrNull { it.status == AgentStatus.IDLE && isAgentEligible(it.id) }
    }
