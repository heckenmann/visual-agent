package de.heckenmann.visualagent.agent.tools.api

/** Todo and result operations needed by the todos tool. */
interface TodoToolPort {
    /** Lists todos. */
    fun list(): List<ToolTodo>

    /** Checks whether an assigned agent exists. */
    fun agentExists(agentId: String): Boolean

    /** Adds a todo and returns its identifier. */
    fun add(
        description: String,
        assignedAgentId: String,
    ): String

    /** Atomically creates a todo or returns the existing normalized-description match. */
    fun addIfAbsent(
        description: String,
        assignedAgentId: String,
    ): ToolTodoCreation

    /** Updates supplied todo fields. */
    fun update(
        id: String,
        description: String?,
        assignedAgentId: String?,
        status: String?,
    )

    /** Applies a todo status transition. */
    fun setStatus(
        id: String,
        status: String,
    ): Boolean

    /** Starts one pending or cancelled todo. */
    fun start(id: String): Boolean

    /** Starts all pending and cancelled todos. */
    fun startAll(): Int

    /** Stops one pending or in-progress todo. */
    fun stop(id: String): Boolean

    /** Stops all pending and in-progress todos. */
    fun stopAll(): Int

    /** Removes a todo. */
    fun remove(id: String): Boolean

    /** Reorders a todo. */
    fun moveToPosition(
        id: String,
        position: Int,
    ): Boolean

    /** Loads the stored todo result. */
    fun result(id: String): String?
}

/** Todo projection used by the tools module. */
data class ToolTodo(
    val id: String,
    val description: String,
    val status: String,
    val position: Int,
    val assignedAgentId: String?,
)

/** Result of an atomic todo creation attempt. */
data class ToolTodoCreation(
    val todo: ToolTodo,
    val created: Boolean,
)
