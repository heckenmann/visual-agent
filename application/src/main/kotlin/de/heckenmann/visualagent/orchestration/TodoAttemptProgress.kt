package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoProgressUpdate
import java.util.UUID

/** Owns stream presentation identities independently of task lifecycle mutation. */
internal class TodoAttemptProgress(
    private val events: TodoEventBus,
    private val todoId: String,
    private val agentId: String,
) {
    var executionId: String = ""
        private set

    var attemptId: String = ""
        private set

    fun begin() {
        attemptId = UUID.randomUUID().toString()
        executionId = attemptId
        publish()
    }

    fun reset() {
        executionId = UUID.randomUUID().toString()
        publish()
    }

    fun persist(
        agent: de.heckenmann.visualagent.agent.SubAgent,
        content: String,
        conversationOps: de.heckenmann.visualagent.agent.ConversationOpsProvider,
        requestId: String,
        attempt: Int,
    ) {
        persistSubAgentMessage(
            agent = agent,
            content = content,
            success = false,
            persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
            attempt = attempt,
            executionId = attemptId,
            todoId = todoId,
        )
    }

    fun publish(
        delta: String = "",
        completed: Boolean = false,
        reviewing: Boolean = false,
    ) {
        events.publishProgress(TodoProgressUpdate(todoId, delta, completed, executionId, agentId, reviewing))
    }
}
