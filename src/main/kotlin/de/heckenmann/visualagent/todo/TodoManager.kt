package de.heckenmann.visualagent.todo

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mutation type emitted when the todo list changes.
 */
enum class TodoChangeType {
    ADDED,
    UPDATED,
    REMOVED,
    CLEARED,
}

/**
 * Event payload sent to todo persistence and UI observers after a mutation.
 *
 * @property type Kind of mutation that occurred
 * @property todo Updated todo for add/update style events
 * @property todoId Removed todo identifier for delete events
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
     * Returns a defensive snapshot of all todos in their current order.
     */
    fun getAll(): List<Todo> = todos.toList()

    /**
     * Returns todos that are ready to be assigned to an agent.
     */
    fun getPending(): List<Todo> = todos.filter { it.status == TodoStatus.PENDING }

    /**
     * Finds one todo by stable identifier.
     *
     * @param id Todo identifier
     * @return Matching todo or null
     */
    fun getById(id: String): Todo? = todos.find { it.id == id }

    /**
     * Returns todos assigned to a specific sub-agent.
     *
     * @param agentId Sub-agent identifier
     */
    fun getByAgent(agentId: String): List<Todo> = todos.filter { it.assignedAgentId == agentId }

    /**
     * Creates a pending todo and publishes an add event.
     *
     * @param description User-facing task description
     * @param priority Initial task priority
     * @return Created todo with generated identifier
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
     * Updates editable todo fields and publishes an update event.
     *
     * @param todoId Identifier of the todo to update
     * @param description New task description
     * @param priority New task priority
     * @return true if the todo exists and was updated
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
     * Assigns a pending todo to an agent and moves it to in-progress.
     *
     * @param todoId Identifier of the pending todo
     * @param agentId Sub-agent that should execute the todo
     * @return true if the todo was pending and is now assigned
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
     * Completes an in-progress todo and records the completion timestamp.
     *
     * @param todoId Identifier of the in-progress todo
     * @return true if the todo could be completed
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
     * Cancels an unfinished todo.
     *
     * @param todoId Identifier of the todo to cancel
     * @return true if the todo existed and was not already terminal
     */
    fun cancelTodo(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.CANCELLED) return false
        todo.status = TodoStatus.CANCELLED
        publishChange(TodoChange(TodoChangeType.UPDATED, todo = todo))
        return true
    }

    /**
     * Removes one todo and publishes a remove event.
     *
     * @param todoId Identifier of the todo to delete
     * @return true if a todo was removed
     */
    fun remove(todoId: String): Boolean {
        val removed = todos.removeIf { it.id == todoId }
        if (removed) publishChange(TodoChange(TodoChangeType.REMOVED, todoId = todoId))
        return removed
    }

    /**
     * Removes all todos and publishes one clear event.
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
