package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a `sub_agent` conversation message with metadata that the UI uses to show
 * success or failure status.
 *
 * @param agent Agent that produced the notification
 * @param content Human-readable result text
 * @param success Whether the agent finished the todo successfully
 * @param persistMessage Callback that persists the message in the conversation
 */
internal fun persistSubAgentMessage(
    agent: SubAgent,
    content: String,
    success: Boolean,
    persistMessage: (Message) -> Unit,
) {
    val metadata =
        buildJsonObject {
            put("type", "sub_agent")
            put("agentId", agent.id)
            put("agentName", agent.name)
            put("success", success)
        }.toString()
    persistMessage(
        Message(
            role = "sub_agent",
            content = content,
            metadata = metadata,
        ),
    )
}

/**
 * Watches a running todo for external changes that should cancel its worker.
 *
 * @param todoId Todo being executed
 * @param assignedAgentId Agent currently assigned to the todo
 * @param taskDescription Instruction the agent is working on
 * @param token Cancellation token to trigger when a relevant change occurs
 * @param todoEventBus Bus that publishes todo changes
 * @return Handle that removes the listener when closed
 */
internal fun startTodoChangeWatcher(
    todoId: String,
    assignedAgentId: String,
    taskDescription: String,
    token: CancellationToken,
    todoEventBus: TodoEventBus,
): AutoCloseable {
    val handle =
        todoEventBus.addListener { change ->
            if (change.todo?.id != todoId && change.todoId != todoId) return@addListener
            when (change.type) {
                TodoChangeType.UPDATED -> {
                    val todo = change.todo ?: return@addListener
                    val reassigned = todo.assignedAgentId != assignedAgentId
                    val cancelled = todo.status == TodoStatus.CANCELLED
                    val descriptionChanged = todo.description != taskDescription
                    if (reassigned || cancelled || descriptionChanged) {
                        token.cancel()
                    }
                }
                TodoChangeType.REMOVED,
                TodoChangeType.CLEARED,
                -> token.cancel()
                else -> Unit
            }
        }
    return handle
}

/**
 * Decides what to do after a running todo worker was cancelled by an external change.
 *
 * @param agent Sub-agent that was executing the todo
 * @param todoId Identifier of the affected todo
 * @param pendingTodoChanges Map of unprocessed changes keyed by todo id
 * @param currentTodo Current persisted state of the todo, if it still exists
 * @param todoManager Manager used to cancel or update the todo
 * @param persistMessage Callback that persists a conversation message
 * @param saveAgentToDb Callback that persists agent state changes
 * @param notifyAgent Callback that sends a status notification to the UI
 * @param onDescriptionChanged Continuation invoked when the todo description changed
 */
internal fun handleTodoChangeAfterCancellation(
    agent: SubAgent,
    todoId: String,
    pendingTodoChanges: MutableMap<String, TodoChange>,
    currentTodo: Todo?,
    todoManager: TodoManager,
    persistMessage: (Message) -> Unit,
    saveAgentToDb: (SubAgent) -> Unit,
    notifyAgent: (String, String) -> Unit,
    onDescriptionChanged: (SubAgent, Todo) -> Unit,
) {
    val change = pendingTodoChanges.remove(todoId)
    when {
        currentTodo == null || currentTodo.status == TodoStatus.CANCELLED || currentTodo.assignedAgentId != agent.id -> {
            todoManager.cancelTodo(todoId)
            val metadata =
                buildJsonObject {
                    put("type", "sub_agent")
                    put("agentId", agent.id)
                    put("agentName", agent.name)
                    put("success", false)
                }.toString()
            persistMessage(
                Message(
                    role = "sub_agent",
                    content =
                        "Agent ${agent.name} (${agent.id}) stopped todo $todoId. " +
                            "Stopped because the todo was cancelled, deleted, or reassigned",
                    metadata = metadata,
                ),
            )
            setAgentIdle(agent, saveAgentToDb, notifyAgent)
        }
        change?.todo != null && change.todo.description != agent.currentTask -> {
            agent.currentTask = currentTodo.description
            saveAgentToDb(agent)
            persistMessage(
                Message(
                    role = "system",
                    content = "Todo $todoId was updated; agent ${agent.id} will continue with the new description.",
                ),
            )
            onDescriptionChanged(agent, currentTodo)
        }
        else -> {
            setAgentIdle(agent, saveAgentToDb, notifyAgent)
        }
    }
}

private fun setAgentIdle(
    agent: SubAgent,
    saveAgentToDb: (SubAgent) -> Unit,
    notifyAgent: (String, String) -> Unit,
) {
    agent.status = AgentStatus.IDLE
    agent.currentTask = null
    agent.currentTodoId = null
    saveAgentToDb(agent)
    notifyAgent(agent.id, "STATUS:${agent.status.name}")
}
