package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.knowledge.TodoCreation
import de.heckenmann.visualagent.knowledge.TodoStore

/** Isolated transient persistence used exclusively by the test-only TodoManager constructor. */
internal class NoOpTodoStore : TodoStore {
    private val rows = java.util.concurrent.ConcurrentHashMap<String, Todo>()

    override fun saveTodo(todo: Todo) {
        rows[todo.id] = todo.copy()
    }

    override fun listTodos(): List<Todo> = rows.values.map { it.copy() }

    override fun deleteTodo(todoId: String) {
        rows.remove(todoId)
    }

    override fun clearTodos() {
        rows.clear()
    }

    override fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo? =
        synchronized(this) {
            val current = rows[todoId]?.takeIf { it.status == TodoStatus.PENDING } ?: return@synchronized null
            current.copy(status = TodoStatus.IN_PROGRESS, assignedAgentId = agentId).also { saveTodo(it) }
        }

    override fun createTodoIfAbsent(todo: Todo): TodoCreation =
        synchronized(this) {
            val existing = rows.values.firstOrNull { it.description.trim().equals(todo.description.trim(), true) }
            if (existing != null) {
                TodoCreation(existing.copy(), false)
            } else {
                saveTodo(todo)
                TodoCreation(todo.copy(), true)
            }
        }
}
