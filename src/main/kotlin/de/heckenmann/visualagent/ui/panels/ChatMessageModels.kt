package de.heckenmann.visualagent.ui.panels.chat

import java.time.Instant

/**
 * UI-ready conversation item rendered by the chat message list.
 *
 * @property role Conversation role such as `user`, `assistant`, or `system`
 * @property content Markdown/text content to render
 * @property timestamp Display timestamp
 * @property isToolEvent Whether this row represents tool execution rather than a normal message
 * @property toolData Optional structured tool-call details
 * @property imageData Optional immutable persisted image details
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Instant = Instant.now(),
    val isToolEvent: Boolean = false,
    val toolData: ToolMessageData? = null,
    val imageData: ImageMessageData? = null,
)

/**
 * Structured preview data for a tool execution row in the conversation.
 *
 * @property toolId Stable tool identifier
 * @property status Execution status shown in the UI
 * @property durationMillis Optional elapsed time
 * @property inputJson Optional compact JSON input preview
 * @property resultContent Optional successful result content
 * @property resultError Optional error message for failed tool calls
 */
data class ToolMessageData(
    val toolId: String,
    val status: String,
    val durationMillis: Long?,
    val inputJson: String?,
    val resultContent: String?,
    val resultError: String?,
)

/**
 * Persisted immutable image attachment displayed in the conversation.
 *
 * @property mimeType MIME type such as `image/png` or `image/jpeg`
 * @property dataUrl Base64 data URL containing the exact persisted bytes
 * @property width Original rendered width in pixels
 * @property height Original rendered height in pixels
 */
data class ImageMessageData(
    val mimeType: String,
    val dataUrl: String,
    val width: Int,
    val height: Int,
)
