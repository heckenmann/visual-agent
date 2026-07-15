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
 * @return The next todo ready for execution, or null if none is available
 */
internal fun findNextAssignableTodo(
    todos: List<Todo>,
    subAgents: Map<String, SubAgent>,
    todoManager: TodoManager,
): Todo? =
    todos
        .filter { it.status == TodoStatus.PENDING }
        .sortedWith(compareBy({ it.position }, { it.id }))
        .firstNotNullOfOrNull { assignAndReturnIfReady(it, subAgents, todoManager) }

private fun assignAndReturnIfReady(
    todo: Todo,
    subAgents: Map<String, SubAgent>,
    todoManager: TodoManager,
): Todo? =
    when {
        !todo.assignedAgentId.isNullOrBlank() ->
            todo.takeIf { subAgents[todo.assignedAgentId]?.status == AgentStatus.IDLE }
        else ->
            subAgents.values.firstOrNull { it.status == AgentStatus.IDLE }?.let { idle ->
                todoManager.updateAssignedAgent(todo.id, idle.id)
                todo
            }
    }
