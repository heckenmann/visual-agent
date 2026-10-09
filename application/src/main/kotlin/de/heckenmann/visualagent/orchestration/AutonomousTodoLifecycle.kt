package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

/** Reports committed completion without turning a notification failure into a worker retry. */
internal fun persistTodoCompletion(
    agent: SubAgent,
    todoId: String,
    result: String,
    requestId: String,
    attempt: Int,
    executionId: String,
    persistMessage: (Message) -> Message,
) {
    runCatching {
        persistSubAgentMessage(
            agent = agent,
            content =
                "Agent ${agent.name} (${agent.id}) completed todo $todoId.\n\n" +
                    "Result:\n${result.take(2000)}\n\nUse `todos` with `get-result` to read the full stored result.",
            success = true,
            persistMessage = { persistMessage(it.copy(conversationRequestId = requestId)) },
            attempt = attempt,
            executionId = executionId,
            todoId = todoId,
        )
    }.onFailure {
        KotlinLogging.logger {}.error(it) { "Could not persist completion notification for todo $todoId" }
    }
}

/** Persists the initial todo notification with the identity shared by its worker. */
internal fun persistTodoStart(
    agent: SubAgent,
    todo: Todo,
    requestId: String,
    persistMessage: (Message) -> Unit,
) {
    persistMessage(
        Message(
            role = "system",
            content = "Started todo ${todo.id} (${todo.description.take(80)}) with agent ${agent.id} (${agent.name}).",
            conversationRequestId = requestId,
        ),
    )
}

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
    attempt: Int? = null,
    executionId: String? = null,
    todoId: String? = null,
) {
    val metadata =
        buildJsonObject {
            put("type", "sub_agent")
            put("eventType", "todo_attempt")
            put("agentId", agent.id)
            put("agentName", agent.name)
            put("todoId", todoId ?: agent.currentTodoId ?: "")
            put("success", success)
            put("status", if (success) "success" else "failure")
            attempt?.let { put("attempt", it) }
            executionId?.let { put("executionId", it) }
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
    executionTimelineSequence: Long = 0,
): AutoCloseable {
    return todoEventBus.addListener { change ->
        if (change.type == TodoChangeType.CLEARED) {
            token.cancel()
            return@addListener
        }
        if (change.todo?.id != todoId && change.todoId != todoId) return@addListener
        if ((change.todo?.timelineSequence ?: Long.MAX_VALUE) < executionTimelineSequence) return@addListener
        when (change.type) {
            TodoChangeType.UPDATED -> {
                val todo = change.todo ?: return@addListener
                val reassigned = todo.assignedAgentId != assignedAgentId
                val cancelled = todo.status != TodoStatus.IN_PROGRESS
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
}

/**
 * Decides what to do after a running todo worker was cancelled by an external change.
 *
 * @param agent Sub-agent that was executing the todo
 * @param todoId Identifier of the affected todo
 * @param pendingTodoChanges Map of unprocessed changes keyed by todo id
 * @param currentTodo Current persisted state of the todo, if it still exists
 * @param persistMessage Callback that persists a conversation message
 * @param saveAgentToDb Callback that persists agent state changes
 * @param onDescriptionChanged Continuation invoked when the todo description changed
 */
internal fun handleTodoChangeAfterCancellation(
    agent: SubAgent,
    todoId: String,
    pendingTodoChanges: MutableMap<String, TodoChange>,
    currentTodo: Todo?,
    persistMessage: (Message) -> Unit,
    saveAgentToDb: (SubAgent) -> Unit,
    releaseAgent: (SubAgent, String) -> Unit,
    onDescriptionChanged: (SubAgent, Todo) -> Unit,
) {
    pendingTodoChanges.remove(todoId)
    when {
        currentTodo == null || currentTodo.status == TodoStatus.CANCELLED || currentTodo.assignedAgentId != agent.id -> {
            val metadata =
                buildJsonObject {
                    put("type", "sub_agent")
                    put("agentId", agent.id)
                    put("agentName", agent.name)
                    put("success", false)
                }.toString()
            try {
                persistMessage(
                    Message(
                        role = "sub_agent",
                        content =
                            "Agent ${agent.name} (${agent.id}) stopped todo $todoId. " +
                                "Stopped because the todo was cancelled, deleted, or reassigned",
                        metadata = metadata,
                    ),
                )
            } finally {
                releaseAgent(agent, todoId)
            }
        }
        currentTodo.status == TodoStatus.IN_PROGRESS &&
            agent.currentTodoId == todoId -> {
            agent.currentTask = currentTodo.description
            saveAgentToDb(agent)
            try {
                persistMessage(
                    Message(
                        role = "system",
                        content = "Todo $todoId was updated; agent ${agent.id} will continue with the new description.",
                    ),
                )
            } finally {
                onDescriptionChanged(agent, currentTodo)
            }
        }
        else -> {
            releaseAgent(agent, todoId)
        }
    }
}

internal fun setAgentIdle(
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
