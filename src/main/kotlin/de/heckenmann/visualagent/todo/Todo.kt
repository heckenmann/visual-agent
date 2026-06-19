package de.heckenmann.visualagent.todo

import java.time.Instant

/**
 * Lifecycle state for a persisted todo item.
 */
enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

/**
 * User-visible urgency used for sorting and agent assignment decisions.
 */
enum class TodoPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT,
}

/**
 * Work item that can be tracked by the user and assigned to sub-agents.
 *
 * @property id Stable todo identifier
 * @property description User-facing task description
 * @property status Current lifecycle state
 * @property priority Urgency used for display and scheduling
 * @property assignedAgentId Optional sub-agent currently responsible for the item
 * @property createdAt Creation timestamp
 * @property completedAt Completion timestamp, set only after completion
 * @property dueDate Optional deadline supplied by the user or planner
 */
data class Todo(
    val id: String,
    var description: String,
    var status: TodoStatus = TodoStatus.PENDING,
    var priority: TodoPriority = TodoPriority.MEDIUM,
    var assignedAgentId: String? = null,
    val createdAt: Instant = Instant.now(),
    var completedAt: Instant? = null,
    val dueDate: Instant? = null,
)
