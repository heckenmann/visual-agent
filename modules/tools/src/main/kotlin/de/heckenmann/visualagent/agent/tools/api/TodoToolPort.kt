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

    /** Creates a contracted task atomically; legacy adapters cannot silently discard it. */
    fun addIfAbsent(
        description: String,
        assignedAgentId: String,
        contract: de.heckenmann.visualagent.task.TaskContract?,
    ): ToolTodoCreation {
        require(contract == null) { "Task contracts are unsupported by this adapter" }
        return addIfAbsent(description, assignedAgentId)
    }

    /** Reads bounded attempt details; the accepted record is distinct from failed attempts. */
    fun attempts(
        id: String,
        offset: Int = 0,
        limit: Int = 10,
    ): List<de.heckenmann.visualagent.task.TaskAttemptResult> = emptyList()

    /** Applies supplied todo fields as one mutation. */
    fun update(request: TodoUpdateRequest): Boolean

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
    val contract: de.heckenmann.visualagent.task.TaskContract? = null,
    val executionGap: String? = null,
)

/** Result of an atomic todo creation attempt. */
data class ToolTodoCreation(
    val todo: ToolTodo,
    val created: Boolean,
)

/** Controls whether an update keeps, clears, or sets an assignment. */
enum class TodoAssignmentMode {
    UNCHANGED,
    CLEAR,
    SET,
}

/** Transport-neutral todo update request with explicit assignment semantics. */
data class TodoUpdateRequest(
    val id: String,
    val description: String? = null,
    val assignmentMode: TodoAssignmentMode = TodoAssignmentMode.UNCHANGED,
    val assignedAgentId: String? = null,
    val status: String? = null,
    val contract: de.heckenmann.visualagent.task.TaskContract? = null,
    val replaceContract: Boolean = false,
)
