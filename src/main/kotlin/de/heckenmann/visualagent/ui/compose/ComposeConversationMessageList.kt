@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message

/**
 * Renders conversation history messages in a [LazyListScope].
 */
internal fun LazyListScope.ConversationMessageList(
    history: List<Message>,
    sending: Boolean,
    deletingMessageIds: Set<String>,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    sendContent: (String) -> Unit,
) {
    if (history.isEmpty()) {
        item {
            PanelEmptyState(
                title = "No conversation yet",
                body = "Send a message to start the main agent session.",
            )
        }
    } else {
        itemsIndexed(history, key = { index, message -> message.id ?: "temp-$index" }) { index, message ->
            val previousRole = history.getOrNull(index - 1)?.role
            val isStreamingPlaceholder =
                message.role == "assistant" && message.content.isBlank() && sending && index == history.lastIndex
            val topPadding = if (previousRole == message.role) 2.dp else 10.dp
            val onDelete: () -> Unit = {
                message.id?.let { id -> onDeleteMessage(id) }
            }
            when (message.role) {
                "tool" ->
                    ToolMessageRow(
                        message = message,
                        isDeleting = message.id in deletingMessageIds,
                        isInFlight = false,
                        onDelete = onDelete,
                        modifier = Modifier.padding(top = topPadding),
                    )
                "system" ->
                    SystemMessageRow(
                        message = message,
                        modifier = Modifier.padding(top = topPadding),
                    )
                else -> {
                    val isStreaming = message.role == "assistant" && sending && index == history.lastIndex
                    MessageRow(
                        message = message,
                        isStreamingPlaceholder = isStreamingPlaceholder,
                        isStreaming = isStreaming,
                        canRetry = message.role == "assistant" && !sending && !isStreamingPlaceholder,
                        canEdit = message.role == "user" && !sending,
                        canDelete = message.id != null && message.role != "system" && message.id !in deletingMessageIds,
                        isDeleting = message.id in deletingMessageIds,
                        onCopied = { onStatusChange("Copied ${message.role} message") },
                        onRetry = {
                            val previousUserMessage = history.take(index).lastOrNull { it.role == "user" }
                            if (previousUserMessage == null) {
                                onStatusChange("No previous user message to retry")
                            } else {
                                onStatusChange("Retrying previous user message...")
                                sendContent(previousUserMessage.content)
                            }
                        },
                        onEdit = { onEditMessage(message.id) },
                        onDelete = onDelete,
                        modifier = Modifier.padding(top = topPadding),
                    )
                }
            }
        }
        if (sending && history.isNotEmpty()) {
            item(key = "streaming-indicator") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Thinking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PulsingDots()
                }
            }
        }
    }
}
