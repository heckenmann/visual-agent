package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.knowledge.TodoCreation
import de.heckenmann.visualagent.knowledge.TodoStore

/** In-memory todo store used by manager unit tests. */
internal class InMemoryTodoStore : TodoStore {
    private val todos = mutableListOf<Todo>()

    @Synchronized
    override fun saveTodo(todo: Todo) {
        todos.removeIf { it.id == todo.id }
        todos.add(todo.copy())
    }

    @Synchronized
    override fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo? {
        val todo = todos.firstOrNull { it.id == todoId && it.status == TodoStatus.PENDING } ?: return null
        todo.assignedAgentId = agentId
        todo.status = TodoStatus.IN_PROGRESS
        todo.updatedAt = java.time.Instant.now()
        return todo.copy()
    }

    @Synchronized
    override fun createTodoIfAbsent(todo: Todo): TodoCreation {
        val normalized =
            todo.description
                .trim()
                .replace(Regex("\\s+"), " ")
                .lowercase()
        val existing =
            todos.firstOrNull {
                it.description
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    .lowercase() == normalized
            }
        return if (existing == null) {
            saveTodo(todo)
            TodoCreation(todo, created = true)
        } else {
            TodoCreation(existing, created = false)
        }
    }

    @Synchronized
    override fun listTodos(): List<Todo> = todos.map { it.copy() }

    @Synchronized
    override fun deleteTodo(todoId: String) {
        todos.removeIf { it.id == todoId }
    }

    @Synchronized
    override fun clearTodos() {
        todos.clear()
    }
}
