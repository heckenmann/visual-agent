package de.heckenmann.visualagent.ui.panels

import java.time.Instant

/**
 * Represents ChatMessage.
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Instant = Instant.now(),
    val isToolEvent: Boolean = false,
    val toolData: ToolMessageData? = null,
)

/**
 * Represents ToolMessageData.
 */
data class ToolMessageData(
    val toolId: String,
    val status: String,
    val durationMillis: Long?,
    val inputJson: String?,
    val resultContent: String?,
    val resultError: String?,
)
