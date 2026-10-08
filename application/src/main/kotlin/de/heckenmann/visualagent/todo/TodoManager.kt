package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.knowledge.TodoStore
import java.util.UUID

/**
 * Mutation type emitted when the todo list changes.
 */
enum class TodoChangeType {
    ADDED,
    UPDATED,
    REMOVED,
    REORDERED,
    CLEARED,
}

/**
 * Event payload sent to todo persistence and UI observers after a mutation.
 *
 * @property type Kind of mutation that occurred
 * @property todo Updated or archived todo for add/update/remove style events
 * @property todoId Removed todo identifier for delete events
 * @property approval Already reviewed feedback for a server-owned completed transition
 */
data class TodoChange(
    val type: TodoChangeType,
    val todo: Todo? = null,
    val todoId: String? = null,
    val previousStatus: TodoStatus? = null,
    val terminalReason: TodoTerminalReason? = null,
    val terminalDetail: String? = null,
    val approval: TodoApproval? = null,
)

/** Owns todo mutations; persisted snapshots are authoritative and events follow successful commits. */
class TodoManager(
    private val todoStore: TodoStore,
    private val eventBus: TodoEventBus,
) {
    /** Test-only constructor backed by an isolated transient store. */
    internal constructor() : this(NoOpTodoStore(), TodoEventBus())

    /** Reads startup state; all later queries also read persistence. */
    fun loadInitialTodos() {
        todoStore.listTodos()
    }

    /** Observes committed state changes. */
    fun addListener(listener: (TodoChange) -> Unit): AutoCloseable = eventBus.addListener(listener)

    /** Returns detached persisted snapshots in display order. */
    fun getAll(): List<Todo> = todoStore.listTodos().map { it.copy() }.sortedBy { it.position }

    /** Returns archived deletion snapshots. */
    fun getDeletedTodos(limit: Int = 100): List<Todo> = todoStore.listDeletedTodos(limit)

    /** Returns pending work in display order. */
    fun getPending(): List<Todo> = getAll().filter { it.status == TodoStatus.PENDING }

    /** Returns a detached snapshot, or null for a missing todo. */
    fun getById(id: String): Todo? = getAll().find { it.id == id }

    /** Returns work assigned to an agent. */
    fun getByAgent(agentId: String): List<Todo> = getAll().filter { it.assignedAgentId == agentId }

    /** Creates an unassigned pending task. */
    fun add(description: String): Todo = create(description, null)

    /** Creates a pending task assigned to the requested worker. */
    fun add(
        description: String,
        assignedAgentId: String,
    ): Todo = create(description, assignedAgentId)

    private fun create(
        description: String,
        agentId: String?,
    ): Todo {
        require(description.isNotBlank()) { "Todo description must not be blank" }
        val todo = Todo(UUID.randomUUID().toString(), description, position = nextPosition(), assignedAgentId = agentId)
        todoStore.saveTodo(todo)
        publish(TodoChange(TodoChangeType.ADDED, todo.copy()))
        return todo.copy()
    }

    /** Creates work once under the store's atomic description guard. */
    fun addIfAbsent(
        description: String,
        assignedAgentId: String,
    ): de.heckenmann.visualagent.knowledge.TodoCreation {
        require(description.isNotBlank()) { "Todo description must not be blank" }
        val creation =
            todoStore.createTodoIfAbsent(
                Todo(UUID.randomUUID().toString(), description, position = nextPosition(), assignedAgentId = assignedAgentId),
            )
        if (creation.created) publish(TodoChange(TodoChangeType.ADDED, creation.todo.copy()))
        return creation.copy(todo = creation.todo.copy())
    }

    /** Updates a description without overwriting concurrent changes. */
    fun update(
        todoId: String,
        description: String,
    ): Boolean = update(TodoUpdateCommand(todoId, description))

    /** Applies one conditional persisted mutation and emits its committed snapshot. */
    internal fun update(
        command: TodoUpdateCommand,
        terminalReason: TodoTerminalReason? = null,
        approval: TodoApproval? = null,
        expected: Todo? = null,
    ): Boolean {
        if (command.description?.isBlank() == true) return false
        if (command.assignment is TodoAssignmentChange.Set && command.assignment.agentId.isBlank()) return false
        val original = expected ?: getById(command.id) ?: return false
        val candidate = original.copy()
        command.description?.let { candidate.description = it }
        when (val assignment = command.assignment) {
            TodoAssignmentChange.Unchanged -> Unit
            TodoAssignmentChange.Clear -> candidate.assignedAgentId = null
            is TodoAssignmentChange.Set -> candidate.assignedAgentId = assignment.agentId
        }
        command.status?.let { status ->
            if (candidate.status != status) {
                candidate.status = status
                candidate.completedAt = if (status == TodoStatus.COMPLETED) java.time.Instant.now() else null
                if (status != TodoStatus.CANCELLED) candidate.terminalDetail = null
            }
        }
        command.terminalDetail?.let { candidate.terminalDetail = it }
        if (candidate == original) return getById(original.id) == original
        candidate.updatedAt = java.time.Instant.now()
        if (!todoStore.updateTodoIfCurrent(original, candidate)) return false
        val reason =
            terminalReason ?: when (candidate.status) {
                TodoStatus.COMPLETED -> TodoTerminalReason.COMPLETED
                TodoStatus.CANCELLED -> TodoTerminalReason.USER_CANCELLED
                else -> null
            }
        publish(
            TodoChange(
                TodoChangeType.UPDATED,
                candidate.copy(),
                previousStatus = original.status,
                terminalReason = reason,
                terminalDetail = candidate.terminalDetail,
                approval = approval,
            ),
        )
        return true
    }

    /** Updates the user-selected status with optimistic conflict detection. */
    fun updateStatus(
        todoId: String,
        status: TodoStatus,
    ): Boolean = update(TodoUpdateCommand(todoId, status = status))

    /** Changes assignment while preserving all other current fields. */
    fun updateAssignedAgent(
        todoId: String,
        agentId: String?,
    ): Boolean =
        update(
            TodoUpdateCommand(todoId, assignment = agentId?.let(TodoAssignmentChange::Set) ?: TodoAssignmentChange.Clear),
        )

    /** Claims pending work atomically in persistence. */
    fun assignToAgent(
        todoId: String,
        agentId: String,
    ): Boolean = claimPendingTodo(todoId, agentId) != null

    /** Claims pending work and emits a detached snapshot. */
    internal fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo? {
        val claimed = todoStore.claimPendingTodo(todoId, agentId) ?: return null
        publish(TodoChange(TodoChangeType.UPDATED, claimed.copy(), previousStatus = TodoStatus.PENDING))
        return claimed.copy()
    }

    /** Completes only the execution snapshot that was actually reviewed. */
    fun completeTodo(
        todoId: String,
        approval: TodoApproval? = null,
        expected: Todo? = null,
    ): Boolean {
        val todo = expected ?: getById(todoId) ?: return false
        if (todo.status != TodoStatus.IN_PROGRESS) return false
        return update(TodoUpdateCommand(todoId, status = TodoStatus.COMPLETED), TodoTerminalReason.COMPLETED, approval, todo)
    }

    /** Cancels unfinished work without overwriting a competing transition. */
    fun cancelTodo(
        todoId: String,
        reason: TodoTerminalReason = TodoTerminalReason.USER_CANCELLED,
        detail: String? = null,
        expected: Todo? = null,
    ): Boolean {
        val todo = expected ?: getById(todoId) ?: return false
        if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.CANCELLED) return false
        return update(TodoUpdateCommand(todoId, status = TodoStatus.CANCELLED, terminalDetail = detail), reason, expected = todo)
    }

    /** Replaces an unchanged parent atomically; children are executable leaves. */
    internal fun replaceWithChildren(
        expected: Todo,
        descriptions: List<String>,
    ): Boolean {
        val children =
            descriptions.mapIndexed { index, description ->
                Todo(
                    UUID.randomUUID().toString(),
                    description,
                    position = nextPosition() + index,
                    decompositionDepth = expected.decompositionDepth + 1,
                )
            }
        if (!todoStore.replaceTodoWithChildren(expected, children)) return false
        val parent = getById(expected.id) ?: return true
        publish(
            TodoChange(TodoChangeType.UPDATED, parent, previousStatus = expected.status, terminalReason = TodoTerminalReason.DECOMPOSED),
        )
        children.forEach { publish(TodoChange(TodoChangeType.ADDED, it.copy())) }
        return true
    }

    /** Moves a todo to a bounded display position. */
    fun moveToPosition(
        todoId: String,
        targetPosition: Int,
    ): Boolean {
        val ordered = getAll().toMutableList()
        val from = ordered.indexOfFirst { it.id == todoId }
        if (from < 0) return false
        val target = targetPosition.coerceIn(0, ordered.lastIndex)
        if (from == target) return true
        ordered.add(target, ordered.removeAt(from))
        return reorder(ordered.map { it.id })
    }

    /** Persists a complete ordering without changing lifecycle state. */
    fun reorder(orderedIds: List<String>): Boolean {
        val current = getAll().associateBy { it.id }
        if (orderedIds.size != current.size || orderedIds.toSet() != current.keys) return false
        val ordered = orderedIds.mapIndexed { index, id -> current.getValue(id).copy(position = index) }
        todoStore.updateTodoPositions(ordered)
        publish(TodoChange(TodoChangeType.REORDERED))
        return true
    }

    /** Archives and removes one todo before notifying active workers. */
    fun remove(todoId: String): Boolean {
        val todo = getById(todoId) ?: return false
        val archived = todoStore.deleteTodoAndArchive(todo)
        publish(TodoChange(TodoChangeType.REMOVED, archived.copy(), todoId))
        return true
    }

    /** Removes all persisted todos and invalidates every active worker. */
    fun clear() {
        todoStore.clearTodos()
        publish(TodoChange(TodoChangeType.CLEARED))
    }

    private fun nextPosition(): Int = (getAll().maxOfOrNull { it.position } ?: -1) + 1

    private fun publish(change: TodoChange) {
        eventBus.publish(change)
    }
}
