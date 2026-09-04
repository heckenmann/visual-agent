package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason

/** Registers main-agent reviews for terminal todo transitions. */
internal fun AgentManager.registerTodoTerminalReviewListener() {
    todoEventBus.addListener { change ->
        if (lifecycle.closing) return@addListener
        val todo = change.todo ?: return@addListener
        if (change.type != TodoChangeType.UPDATED) return@addListener
        if (change.previousStatus == null || change.previousStatus == todo.status) return@addListener
        when (todo.status) {
            TodoStatus.COMPLETED, TodoStatus.CANCELLED ->
                todoTrigger.trigger(todo, change.terminalReason ?: TodoTerminalReason.USER_CANCELLED)
            else -> Unit
        }
    }
}
