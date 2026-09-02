package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.startAllTodos
import de.heckenmann.visualagent.agent.startTodo
import de.heckenmann.visualagent.agent.stopAllTodos
import de.heckenmann.visualagent.agent.stopTodo
import de.heckenmann.visualagent.protocol.AgentSummary
import de.heckenmann.visualagent.protocol.TodoChange
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoResponseSnapshot
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.protocol.TodoUpdate
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/** Spring adapter that keeps todo persistence and agent execution behind [TodoPort]. */
@Component
class SpringTodoPort(
    private val agentManager: AgentManager,
    private val todoEventBus: TodoEventBus,
) : TodoPort {
    override fun list(): List<TodoItem> = agentManager.getTodosFromDb().map(Todo::toTodoItem)

    override fun deletedSnapshots(limit: Int): List<TodoItem> = agentManager.todoManager.getDeletedTodos(limit).map(Todo::toTodoItem)

    override fun responseSnapshots(todoIds: Set<String>): List<TodoResponseSnapshot> =
        todoIds.mapNotNull { todoId ->
            agentManager.memoryStore.searchMemories("todo:$todoId", limit = 1).firstOrNull()?.let { memory ->
                TodoResponseSnapshot(todoId = todoId, text = memory.todoResponseText())
            }
        }

    override fun agents(): List<AgentSummary> = agentManager.getSubAgents().map(SubAgent::toAgentSummary)

    override fun add(description: String): TodoItem = agentManager.todoManager.add(description).toTodoItem()

    override fun updateDescription(
        todoId: String,
        description: String,
    ): Boolean = agentManager.todoManager.update(todoId, description)

    override fun update(update: TodoUpdate): Boolean =
        agentManager.todoManager.update(
            de.heckenmann.visualagent.todo.TodoUpdateCommand(
                id = update.todoId,
                description = update.description,
                assignment =
                    update.assignedAgentId?.let(de.heckenmann.visualagent.todo.TodoAssignmentChange::Set)
                        ?: de.heckenmann.visualagent.todo.TodoAssignmentChange.Clear,
                status = update.status.toTodoStatus(),
            ),
        )

    override fun updateStatus(
        todoId: String,
        status: TodoState,
    ): Boolean = agentManager.todoManager.updateStatus(todoId, status.toTodoStatus())

    override fun updateAssignedAgent(
        todoId: String,
        agentId: String?,
    ): Boolean = agentManager.todoManager.updateAssignedAgent(todoId, agentId)

    override fun start(todoId: String): Boolean = agentManager.startTodo(todoId)

    override fun stop(todoId: String): Boolean = agentManager.stopTodo(todoId)

    override fun startAll() {
        agentManager.startAllTodos()
    }

    override fun stopAll() {
        agentManager.stopAllTodos()
    }

    override fun reorder(orderedIds: List<String>): Boolean = agentManager.todoManager.reorder(orderedIds)

    override fun remove(todoId: String): Boolean = agentManager.todoManager.remove(todoId)

    override fun addListener(listener: (TodoChange) -> Unit): AutoCloseable =
        todoEventBus.addListener { change ->
            listener(
                TodoChange(
                    todo = change.todo?.toTodoItem(),
                    todoId = change.todoId,
                    removed = change.type == de.heckenmann.visualagent.todo.TodoChangeType.REMOVED,
                ),
            )
        }

    override fun addProgressListener(listener: (TodoProgress) -> Unit): AutoCloseable =
        todoEventBus.addProgressListener { update ->
            listener(
                TodoProgress(
                    todoId = update.todoId,
                    delta = update.delta,
                    completed = update.completed,
                    executionId = update.executionId,
                    agentId = update.agentId,
                ),
            )
        }
}

private fun Todo.toTodoItem(): TodoItem =
    TodoItem(
        id = id,
        description = description,
        status = status.toTodoState(),
        position = position,
        assignedAgentId = assignedAgentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        timelineSequence = timelineSequence,
        completedAt = completedAt,
        dueDate = dueDate,
    )

private fun SubAgent.toAgentSummary(): AgentSummary = AgentSummary(id = id, name = name)

private fun TodoStatus.toTodoState(): TodoState =
    when (this) {
        TodoStatus.PENDING -> TodoState.PENDING
        TodoStatus.IN_PROGRESS -> TodoState.IN_PROGRESS
        TodoStatus.COMPLETED -> TodoState.COMPLETED
        TodoStatus.CANCELLED -> TodoState.CANCELLED
    }

private fun TodoState.toTodoStatus(): TodoStatus =
    when (this) {
        TodoState.PENDING -> TodoStatus.PENDING
        TodoState.IN_PROGRESS -> TodoStatus.IN_PROGRESS
        TodoState.COMPLETED -> TodoStatus.COMPLETED
        TodoState.CANCELLED -> TodoStatus.CANCELLED
    }

private fun de.heckenmann.visualagent.knowledge.Memory.todoResponseText(): String =
    runCatching {
        Json
            .parseToJsonElement(content)
            .jsonObject["summary"]
            ?.jsonPrimitive
            ?.content
    }.getOrNull()?.takeIf(String::isNotBlank) ?: content
