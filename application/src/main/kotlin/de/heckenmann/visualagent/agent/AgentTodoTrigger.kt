package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps
import de.heckenmann.visualagent.agent.text.AgentResponseCoordinator
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import de.heckenmann.visualagent.agent.tools.api.ToolResult as ToolsToolResult

/**
 * Handles triggering the main agent when a todo changes status.
 *
 * Extracted from [AgentManager] to keep file sizes manageable.
 */
internal class AgentTodoTrigger(
    private val scope: CoroutineScope,
    private val conversationOps: AgentManagerConversationOps,
    private val llmProvider: LLMProvider,
    private val responseCoordinator: AgentResponseCoordinator,
    private val toolEventBus: ToolEventBus,
    private val lifecycle: LifecyclePort,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Triggers the main agent to process a todo change notification.
     * The agent's response is persisted to the conversation history.
     * On LLM failure, a system message is persisted instead and the error is logged.
     * A synthetic [ToolCallEvent] is always published so the UI refreshes.
     */
    fun trigger(todo: Todo) {
        if (lifecycle.closing) return
        scope.launch {
            if (lifecycle.closing) return@launch
            currentCoroutineContext().ensureActive()
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
                    else -> return@launch
                }
            if (lifecycle.closing) return@launch
            conversationOps.persist(
                Message(
                    role = "system",
                    content = "The todo \"${todo.description}\" (id=${todo.id}) $action",
                ),
            )
            val requestId = "todo-trigger-${todo.id}"
            val activityRequestId = "$requestId:activity"
            toolEventBus.publish(
                ToolCallEvent(
                    toolId = "todos",
                    functionName = "todos",
                    phase = ToolCallPhase.STARTED,
                    inputJson = "{}",
                    context = mapOf("trigger" to "todoChange", "requestId" to activityRequestId),
                    result = ToolsToolResult(toolId = "todos", success = true, content = ""),
                    startedAtUtc = java.time.Instant.now(),
                    finishedAtUtc = java.time.Instant.now(),
                    durationMillis = 0L,
                ),
            )
            val history = conversationOps.loadRecentHistoryFromDb()
            val request =
                conversationOps.buildMainRequest(
                    appendTodoChangeReviewInput(history, todo),
                    requestId,
                )
            try {
                currentCoroutineContext().ensureActive()
                val response = llmProvider.chat(request)
                val content = responseCoordinator.normalizeAssistantPresentationContent(response.message.content)
                if (lifecycle.closing) return@launch
                conversationOps.persist(Message(role = "assistant", content = content))
            } catch (cancelled: CancellationException) {
                // Cancellation is expected while the application is shutting down. Do not
                // log it as a failed trigger or attempt a database write after cancellation.
                return@launch
            } catch (error: Throwable) {
                if (lifecycle.closing) return@launch
                logger.warn(error) { "triggerMainAgentOnTodoChange failed" }
                conversationOps.persist(
                    Message(
                        role = "system",
                        content =
                            "The main agent could not be triggered to review a todo change: " +
                                "${error.message ?: error::class.simpleName ?: "unknown error"}.",
                    ),
                )
            }
            if (lifecycle.closing) return@launch
            toolEventBus.publish(
                ToolCallEvent(
                    toolId = "todos",
                    functionName = "todos",
                    phase = ToolCallPhase.FINISHED,
                    inputJson = "{}",
                    context = mapOf("trigger" to "todoChange", "requestId" to activityRequestId),
                    result = ToolsToolResult(toolId = "todos", success = true, content = ""),
                    startedAtUtc = java.time.Instant.now(),
                    finishedAtUtc = java.time.Instant.now(),
                    durationMillis = 0L,
                ),
            )
        }
    }
}

/**
 * Adds the explicit user turn required to initiate an autonomous todo review.
 *
 * The instruction is intentionally request-local: persisting it would misrepresent an
 * automated follow-up as a message authored by the user.
 */
internal fun appendTodoChangeReviewInput(
    history: List<Message>,
    todo: Todo,
): List<Message> =
    history +
        Message(
            role = "user",
            content =
                "Review the todo with id=${todo.id} described in the preceding system notification " +
                    "and carry out its instructions. Do not substitute another todo.",
        )
