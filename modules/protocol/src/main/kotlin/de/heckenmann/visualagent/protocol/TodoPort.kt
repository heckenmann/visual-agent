package de.heckenmann.visualagent.protocol

import java.time.Instant

/** Lifecycle state of a persisted todo item at the presentation boundary. */
enum class TodoState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

/** Immutable todo record exchanged between a desktop client and the application server. */
data class TodoItem(
    val id: String,
    val description: String,
    val status: TodoState = TodoState.PENDING,
    val position: Int = 0,
    val assignedAgentId: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val completedAt: Instant? = null,
    val dueDate: Instant? = null,
)

/** Minimal agent identity needed by todo assignment controls. */
data class AgentSummary(
    val id: String,
    val name: String,
)

/** Todo mutation delivered to a presentation client. */
data class TodoChange(
    val todo: TodoItem? = null,
    val todoId: String? = null,
)

/** Incremental assistant output produced while a todo is processing. */
data class TodoProgress(
    val todoId: String,
    val delta: String = "",
    val completed: Boolean = false,
    val executionId: String? = null,
    val agentId: String? = null,
)

/** Transport-neutral todo operations used by the todo panel. */
interface TodoPort {
    /** Reads the current persisted todo list. */
    fun list(): List<TodoItem>

    /** Reads deleted todo snapshots retained for conversation reconstruction. */
    fun deletedSnapshots(): List<TodoItem> = emptyList()

    /** Reads agents available for assignment. */
    fun agents(): List<AgentSummary>

    /** Creates a pending todo. */
    fun add(description: String): TodoItem

    /** Updates a todo description. */
    fun updateDescription(
        todoId: String,
        description: String,
    ): Boolean

    /** Updates a todo lifecycle state. */
    fun updateStatus(
        todoId: String,
        status: TodoState,
    ): Boolean

    /** Updates the assigned agent, or clears it when null. */
    fun updateAssignedAgent(
        todoId: String,
        agentId: String?,
    ): Boolean

    /** Starts one todo when it is startable. */
    fun start(todoId: String): Boolean

    /** Stops one todo without changing completed items. */
    fun stop(todoId: String): Boolean

    /** Starts all startable todos. */
    fun startAll()

    /** Stops all active todos while preserving completed items. */
    fun stopAll()

    /** Reorders todos by their identifiers. */
    fun reorder(orderedIds: List<String>): Boolean

    /** Removes one todo. */
    fun remove(todoId: String): Boolean

    /** Observes persisted todo mutations. */
    fun addListener(listener: (TodoChange) -> Unit): AutoCloseable

    /** Observes transient streaming output for an active todo. */
    fun addProgressListener(listener: (TodoProgress) -> Unit): AutoCloseable
}
