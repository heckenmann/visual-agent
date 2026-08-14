package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus

/**
 * Selects the next pending todo that can be started and assigns it to an idle agent
 * when no agent is currently assigned.
 *
 * @param todos Current todo list
 * @param subAgents Available sub-agents keyed by id
 * @param todoManager Manager used to persist an auto-assignment
 * @param requestedTodoId Optional todo id to restrict selection to one explicit request
 * @param isAgentEligible Gate checked before an assigned or auto-selected agent is used
 * @return The next todo ready for execution, or null if none is available
 */
internal fun findNextAssignableTodo(
    todos: List<Todo>,
    subAgents: Map<String, SubAgent>,
    todoManager: TodoManager,
    requestedTodoId: String? = null,
    isAgentEligible: (String) -> Boolean = { true },
): Todo? =
    todos
        .filter { it.status == TodoStatus.PENDING && (requestedTodoId == null || it.id == requestedTodoId) }
        .sortedWith(compareBy({ it.position }, { it.id }))
        .firstNotNullOfOrNull { assignAndReturnIfReady(it, subAgents, todoManager, isAgentEligible) }

private fun assignAndReturnIfReady(
    todo: Todo,
    subAgents: Map<String, SubAgent>,
    todoManager: TodoManager,
    isAgentEligible: (String) -> Boolean,
): Todo? =
    when {
        !todo.assignedAgentId.isNullOrBlank() ->
            todo.takeIf {
                val agentId = todo.assignedAgentId ?: return@takeIf false
                isAgentEligible(agentId) && subAgents[agentId]?.status == AgentStatus.IDLE
            }
        else ->
            subAgents.values.firstOrNull { it.status == AgentStatus.IDLE && isAgentEligible(it.id) }?.let { idle ->
                todoManager.updateAssignedAgent(todo.id, idle.id)
                todo.copy(assignedAgentId = idle.id)
            }
    }
