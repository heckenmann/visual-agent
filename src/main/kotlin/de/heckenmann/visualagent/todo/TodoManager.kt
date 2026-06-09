package de.heckenmann.visualagent.todo

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Represents TodoChangeType.
 */
enum class TodoChangeType {
    ADDED,
    UPDATED,
    REMOVED,
    CLEARED,
}

/**
 * Represents TodoChange.
 */
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
    private val listeners = CopyOnWriteArrayList<(TodoChange) -> Unit>()

    /**
     * Register a listener that receives all todo change events.
     *
     * @param listener Callback invoked after each state mutation
     * @return Handle that removes the listener when closed
     */
    fun addListener(listener: (TodoChange) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Executes getAll.
     */
    fun getAll(): List<Todo> = todos.toList()

    /**
     * Executes getPending.
     */
    fun getPending(): List<Todo> = todos.filter { it.status == TodoStatus.PENDING }

    /**
     * Executes getById.
     */
    fun getById(id: String): Todo? = todos.find { it.id == id }

    /**
     * Executes getByAgent.
     */
    fun getByAgent(agentId: String): List<Todo> = todos.filter { it.assignedAgentId == agentId }

    /**
     * Executes add.
     */
    fun add(
        description: String,
        priority: TodoPriority = TodoPriority.MEDIUM,
    ): Todo {
        val todo =
            Todo(
                id = UUID.randomUUID().toString(),
                description = description,
                priority = priority,
                status = TodoStatus.PENDING,
            )
        todos.add(todo)
        publishChange(TodoChange(TodoChangeType.ADDED, todo = todo))
        return todo
    }

    /**
     * Executes update.
     */
    fun update(
        todoId: String,
        description: String,
        priority: TodoPriority,
    ): Boolean {
        val todo = getById(todoId) ?: return false
        todo.description = description
        todo.priority = priority
        publishChange(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    /**
     * Updates a todo status and publishes the change for persistence and UI observers.
     *
     * @param todoId Identifier of the todo to update
     * @param status New lifecycle status
     * @return true if the todo exists and was updated
     */
    fun updateStatus(
        todoId: String,
        status: TodoStatus,
    ): Boolean {
        val todo = getById(todoId) ?: return false
        todo.status = status
        todo.completedAt = if (status == TodoStatus.COMPLETED) java.time.Instant.now() else null
        publishChange(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    /**
     * Executes assignToAgent.
     */
    fun assignToAgent(
        todoId: String,
        agentId: String,
    ): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status != TodoStatus.PENDING) return false
        todo.assignedAgentId = agentId
        todo.status = TodoStatus.IN_PROGRESS
        publishChange(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    /**
     * Executes completeTodo.
     */
    fun completeTodo(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status != TodoStatus.IN_PROGRESS) return false
        todo.status = TodoStatus.COMPLETED
        todo.completedAt = java.time.Instant.now()
        publishChange(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    /**
     * Executes cancelTodo.
     */
    fun cancelTodo(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.CANCELLED) return false
        todo.status = TodoStatus.CANCELLED
        publishChange(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    /**
     * Executes remove.
     */
    fun remove(todoId: String): Boolean {
        val removed = todos.removeIf { it.id == todoId }
        if (removed) publishChange(TodoChange(TodoChangeType.REMOVED, todoId = todoId))
        return removed
    }

    /**
     * Executes clear.
     */
    fun clear() {
        todos.clear()
        publishChange(TodoChange(TodoChangeType.CLEARED))
    }

    /**
     * Publishes one change event to legacy callback and registered listeners.
     *
     * @param change Event payload
     */
    private fun publishChange(change: TodoChange) {
        onChange?.invoke(change)
        listeners.forEach { listener ->
            runCatching { listener(change) }
        }
    }
}
