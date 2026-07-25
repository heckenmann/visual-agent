package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus

/**
 * Persists a user message into the conversation when a todo changes status.
 *
 * The message is picked up by the conversation panel's [de.heckenmann.visualagent.todo.TodoEventBus]
 * listener and enqueued into the message queue, which sends it through the normal
 * [de.heckenmann.visualagent.ui.compose.executeSend] path with streaming and indicators.
 *
 * Extracted from [AgentManager] to keep file sizes manageable.
 */
internal class AgentTodoTrigger(
    private val conversationOps: AgentManagerConversationOps,
) {
    /**
     * Persists a user message describing the todo change.
     * The conversation panel will pick it up and send it to the main agent.
     */
    fun trigger(todo: Todo) {
        val action =
            when (todo.status) {
                TodoStatus.COMPLETED ->
                    "was just completed by the sub-agent. " +
                        "Review the result. If the task was done correctly, inform the user. " +
                        "Do NOT create a new todo for this task. " +
                        "If the result is incomplete or incorrect, update the todo description " +
                        "with better instructions and set it back to PENDING."
                TodoStatus.CANCELLED ->
                    "was cancelled. Inform the user. " +
                        "If the task still needs to be done, update the todo description " +
                        "and set it back to PENDING."
                else -> return
            }
        conversationOps.persist(
            Message(
                role = "user",
                content = "The todo \"${todo.description}\" (id=${todo.id}) $action",
            ),
        )
    }
}
