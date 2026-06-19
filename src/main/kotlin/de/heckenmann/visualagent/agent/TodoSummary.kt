package de.heckenmann.visualagent.agent

/**
 * Immutable todo summary snapshot sourced from the database.
 *
 * @property total Total number of todos
 * @property open Number of pending todos
 * @property inProgress Number of in-progress todos
 * @property completed Number of completed todos
 * @property cancelled Number of cancelled todos
 */
data class TodoSummary(
    val total: Int,
    val open: Int,
    val inProgress: Int,
    val completed: Int,
    val cancelled: Int,
)
