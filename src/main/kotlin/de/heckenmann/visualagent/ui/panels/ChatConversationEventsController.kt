package de.heckenmann.visualagent.ui.panels.chat

import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import javafx.beans.property.SimpleBooleanProperty
import java.time.Instant

/**
 * Encapsulates assistant/tool event handling for the conversation panel.
 *
 * @property messageList Backing message list controller
 * @property loadingToken Placeholder token for pending assistant responses
 * @property waitingForAssistant Busy-state property bound to UI controls
 * @property updateRuntimeStatus Callback to refresh runtime indicator state
 * @property sendMessage Retry callback for resubmitting user messages
 * @property mapToolEvent Converts tool events to conversation messages
 */
internal class ChatConversationEventsController(
    private val messageList: ChatMessageListController,
    private val loadingToken: String,
    private val waitingForAssistant: SimpleBooleanProperty,
    private val updateRuntimeStatus: () -> Unit,
    private val sendMessage: (String) -> Unit,
    private val mapToolEvent: (ToolCallEvent) -> ChatMessage,
) {
    /**
     * Appends or replaces the assistant placeholder with final response text.
     *
     * @param text Assistant response text
     */
    fun addAssistantMessage(text: String) {
        val normalizedText = normalizeAssistantText(text)
        val loadingIndex = messageList.latestLoadingIndex()
        if (loadingIndex >= 0) {
            messageList.replace(loadingIndex, messageList.messages[loadingIndex].copy(content = normalizedText))
            waitingForAssistant.set(false)
            updateRuntimeStatus()
            messageList.scrollToBottom()
            return
        }
        messageList.append(ChatMessage("assistant", normalizedText))
        waitingForAssistant.set(false)
        updateRuntimeStatus()
    }

    /**
     * Updates the current streaming assistant placeholder.
     *
     * @param content Aggregated streamed content
     */
    fun updateStreamingAssistantMessage(content: String) {
        messageList.updateStreaming(content)
    }

    /**
     * Finalizes streaming and clears assistant busy state.
     *
     * @param finalText Final assistant text after streaming
     */
    fun finishStreamingAssistantMessage(finalText: String) {
        val normalized = normalizeAssistantText(finalText)
        messageList.updateStreaming(normalized)
        messageList.scrollToBottom(force = true)
        waitingForAssistant.set(false)
        updateRuntimeStatus()
    }

    /**
     * Inserts a finished tool-call event into conversation history.
     *
     * @param event Emitted tool event
     */
    fun addToolCallEvent(event: ToolCallEvent) {
        if (event.phase != ToolCallPhase.FINISHED) return
        val eventMessage = mapToolEvent(event)
        val loadingIndex = messageList.latestLoadingIndex()
        if (loadingIndex >= 0) {
            messageList.insert(loadingIndex, eventMessage)
        } else {
            messageList.append(eventMessage)
        }
    }

    /**
     * Adds one synthetic tool-style thinking event entry.
     *
     * @param thinkingContent Extracted thinking block content
     */
    fun addThinkingEvent(thinkingContent: String) {
        if (thinkingContent.isBlank()) return
        val now = Instant.now()
        addToolCallEvent(
            ToolCallEvent(
                toolId = "thinking",
                functionName = "thinking",
                phase = ToolCallPhase.FINISHED,
                inputJson = "",
                context = emptyMap(),
                result = ToolResult(toolId = "thinking", success = true, content = thinkingContent.trim()),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 0,
            ),
        )
    }

    /**
     * Retries by resending the nearest preceding user message for the selected assistant row.
     *
     * @param assistantIndex Index of assistant message row
     */
    fun retryAssistantAt(assistantIndex: Int) {
        if (assistantIndex < 0 || assistantIndex >= messageList.messages.size) return
        for (index in assistantIndex - 1 downTo 0) {
            val message = messageList.messages[index]
            if (message.role == "user") {
                messageList.replace(assistantIndex, messageList.messages[assistantIndex].copy(content = loadingToken))
                waitingForAssistant.set(true)
                updateRuntimeStatus()
                messageList.scrollToBottom(force = true)
                sendMessage(message.content)
                return
            }
        }
    }

    /**
     * Normalizes assistant output when tool-only turns produced no visible text.
     *
     * @param text Raw assistant text
     * @return Non-empty displayable text
     */
    private fun normalizeAssistantText(text: String): String =
        if (text.isBlank()) {
            "(No text response. See tool results above.)"
        } else {
            text
        }
}
