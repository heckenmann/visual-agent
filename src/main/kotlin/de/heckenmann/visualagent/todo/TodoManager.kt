package de.heckenmann.visualagent.todo

import java.util.UUID

enum class TodoChangeType {
    ADDED,
    UPDATED,
    REMOVED,
    CLEARED,
}

data class TodoChange(
    val type: TodoChangeType,
    val todo: Todo? = null,
    val todoId: String? = null,
)

/**
 * Manages an in-memory list of [Todo] items with CRUD operations and
 * agent assignment support.
 */
class TodoManager(
    initialTodos: List<Todo> = emptyList(),
    private val onChange: ((TodoChange) -> Unit)? = null,
) {

    private val todos = initialTodos.toMutableList()

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
        onChange?.invoke(TodoChange(TodoChangeType.ADDED, todo = todo))
        return todo
    }

    fun assignToAgent(todoId: String, agentId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status != TodoStatus.PENDING) return false
        todo.assignedAgentId = agentId
        todo.status = TodoStatus.IN_PROGRESS
        onChange?.invoke(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    fun completeTodo(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status != TodoStatus.IN_PROGRESS) return false
        todo.status = TodoStatus.COMPLETED
        todo.completedAt = java.time.Instant.now()
        onChange?.invoke(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    fun cancelTodo(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.CANCELLED) return false
        todo.status = TodoStatus.CANCELLED
        onChange?.invoke(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    fun remove(todoId: String): Boolean {
        val removed = todos.removeIf { it.id == todoId }
        if (removed) onChange?.invoke(TodoChange(TodoChangeType.REMOVED, todoId = todoId))
        return removed
    }

    fun clear() {
        todos.clear()
        onChange?.invoke(TodoChange(TodoChangeType.CLEARED))
    }
}
