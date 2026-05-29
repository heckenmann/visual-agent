package de.heckenmann.visualagent.todo

import java.util.UUID

/**
 * Manages an in-memory list of [Todo] items with CRUD operations and
 * agent assignment support.
 */
class TodoManager {

    private val todos = mutableListOf<Todo>()

    fun getAll(): List<Todo> = todos.toList()

    fun getPending(): List<Todo> = todos.filter { it.status == TodoStatus.PENDING }

    fun getById(id: String): Todo? = todos.find { it.id == id }

    fun getByAgent(agentId: String): List<Todo> =
        todos.filter { it.assignedAgentId == agentId }

    fun add(description: String, priority: TodoPriority = TodoPriority.MEDIUM): Todo {
        val todo = Todo(
            id = UUID.randomUUID().toString(),
            description = description,
            priority = priority,
            status = TodoStatus.PENDING,
        )
        todos.add(todo)
        return todo
    }

    fun assignToAgent(todoId: String, agentId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status != TodoStatus.PENDING) return false
        todo.assignedAgentId = agentId
        todo.status = TodoStatus.IN_PROGRESS
        return true
    }

    fun completeTodo(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status != TodoStatus.IN_PROGRESS) return false
        todo.status = TodoStatus.COMPLETED
        todo.completedAt = java.time.Instant.now()
        return true
    }

    fun cancelTodo(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.CANCELLED) return false
        todo.status = TodoStatus.CANCELLED
        return true
    }

    fun remove(todoId: String): Boolean = todos.removeIf { it.id == todoId }

    fun clear() {
        todos.clear()
    }
}
