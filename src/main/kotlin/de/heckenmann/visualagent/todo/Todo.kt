package de.heckenmann.visualagent.todo

import java.time.Instant

/**
 * Represents TodoStatus.
 */
enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

/**
 * Represents TodoPriority.
 */
enum class TodoPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT,
}

/**
 * Represents Todo.
 */
data class Todo(
    val id: String,
    val description: String,
    var status: TodoStatus = TodoStatus.PENDING,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    var assignedAgentId: String? = null,
    val createdAt: Instant = Instant.now(),
    var completedAt: Instant? = null,
    val dueDate: Instant? = null,
)
