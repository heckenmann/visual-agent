package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.conversation.AgentManagerConversationOps
import de.heckenmann.visualagent.agent.text.AgentResponseCoordinator
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

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
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Triggers the main agent to process a todo change notification.
     * The agent's response is persisted to the conversation history.
     * On LLM failure, a system message is persisted instead and the error is logged.
     * A synthetic [ToolCallEvent] is always published so the UI refreshes.
     */
    fun trigger() {
        scope.launch {
            val history = conversationOps.loadRecentHistoryFromDb()
            val request = conversationOps.buildMainRequest(history)
            val result =
                runCatching {
                    val response = llmProvider.chat(request)
                    val content = responseCoordinator.normalizeAssistantContent(response.message.content)
                    conversationOps.persist(Message(role = "assistant", content = content))
                }
            result.onFailure { error ->
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
            toolEventBus.publish(
                ToolCallEvent(
                    toolId = "todos",
                    functionName = "todos",
                    phase = ToolCallPhase.FINISHED,
                    inputJson = "{}",
                    context = mapOf("trigger" to "todoChange"),
                    result = ToolResult(toolId = "todos", success = true, content = ""),
                    startedAtUtc = java.time.Instant.now(),
                    finishedAtUtc = java.time.Instant.now(),
                    durationMillis = 0L,
                ),
            )
        }
    }
}
