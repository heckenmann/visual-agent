package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo

/** In-memory store for coordinator tests, with the same claim semantics as persistence. */
internal class FakeTodoStore : TodoStore {
    private val todos = mutableListOf<Todo>()

    override fun saveTodo(todo: Todo) {
        todos.removeIf { it.id == todo.id }
        todos.add(todo)
    }

    override fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo? {
        val todo = todos.firstOrNull { it.id == todoId && it.status == de.heckenmann.visualagent.todo.TodoStatus.PENDING } ?: return null
        todo.assignedAgentId = agentId
        todo.status = de.heckenmann.visualagent.todo.TodoStatus.IN_PROGRESS
        todo.updatedAt = java.time.Instant.now()
        return todo.copy()
    }

    override fun createTodoIfAbsent(todo: Todo): de.heckenmann.visualagent.knowledge.TodoCreation {
        val existing = todos.firstOrNull { it.description.equals(todo.description, ignoreCase = true) }
        return if (existing == null) {
            saveTodo(todo)
            de.heckenmann.visualagent.knowledge
                .TodoCreation(todo, created = true)
        } else {
            de.heckenmann.visualagent.knowledge
                .TodoCreation(existing, created = false)
        }
    }

    override fun listTodos(): List<Todo> = todos.toList()

    override fun deleteTodo(todoId: String) {
        todos.removeIf { it.id == todoId }
    }

    override fun clearTodos() {
        todos.clear()
    }
}
