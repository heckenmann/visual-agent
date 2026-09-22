package de.heckenmann.visualagent.agent.context

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus

/** Builds the optional current-state prompt for the main agent. */
internal object MainAgentRuntimeStatePrompt {
    /**
     * Composes the complete persisted todo and sub-agent inventory.
     *
     * The request budgeter decides whether this optional state can be sent intact. It never
     * silently presents a partial inventory as the authoritative state.
     *
     * @param todos Current persisted todo list
     * @param subAgents Current persisted sub-agent inventory
     * @return Current runtime state for a low-priority provider reference message
     */
    fun compose(
        todos: List<Todo>,
        subAgents: List<SubAgent>,
    ): String {
        val counts = todoCounts(todos)
        val todoLines = todos.joinToString("\n", transform = ::todoLine).ifBlank { "- none" }
        val agentLines = subAgents.joinToString("\n", transform = ::agentLine).ifBlank { "- none" }
        return """
            ## Runtime State
            TODO counts: open=${counts.open}, inProgress=${counts.inProgress}, done=${counts.done}, cancelled=${counts.cancelled}, total=${counts.total}
            Current todos:
            $todoLines
            Persisted sub-agents:
            $agentLines
            """.trimIndent()
    }

    private fun todoLine(todo: Todo): String =
        "- [${todo.status}] ${todo.description} (id=${todo.id}, assigned=${todo.assignedAgentId ?: "none"})"

    private fun agentLine(agent: SubAgent): String = "- ${agent.name} (id=${agent.id}, role=${agent.role}, status=${agent.status})"

    private fun todoCounts(todos: List<Todo>): TodoCounts =
        TodoCounts(
            open = todos.count { it.status == TodoStatus.PENDING },
            inProgress = todos.count { it.status == TodoStatus.IN_PROGRESS },
            done = todos.count { it.status == TodoStatus.COMPLETED },
            cancelled = todos.count { it.status == TodoStatus.CANCELLED },
            total = todos.size,
        )

    private data class TodoCounts(
        val open: Int,
        val inProgress: Int,
        val done: Int,
        val cancelled: Int,
        val total: Int,
    )
}
