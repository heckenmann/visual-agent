package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason

/** Makes orphaned execution state restartable without replaying unknown external side effects. */
internal fun recoverInterruptedTodos(manager: TodoManager) {
    manager.getAll().filter { it.status == TodoStatus.IN_PROGRESS }.forEach { todo ->
        manager.cancelTodo(
            todo.id,
            TodoTerminalReason.INTERRUPTED,
            "Execution was interrupted by a server restart. Inspect existing work before explicitly retrying.",
            expected = todo,
        )
    }
}
