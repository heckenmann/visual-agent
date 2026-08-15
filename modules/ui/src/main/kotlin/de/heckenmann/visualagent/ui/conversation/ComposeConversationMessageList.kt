@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

internal sealed interface ConversationTimelineItem {
    val stableKey: String

    data object InlineComposer : ConversationTimelineItem {
        override val stableKey = "conversation-input"
    }

    data object Waiting : ConversationTimelineItem {
        override val stableKey = "streaming-indicator"
    }

    data class Streaming(
        val content: String,
    ) : ConversationTimelineItem {
        override val stableKey = "streaming-assistant"
    }

    data class PendingUser(
        val content: String,
    ) : ConversationTimelineItem {
        override val stableKey = "pending-user"
    }

    data class Persisted(
        val message: Message,
        val chronologicalIndex: Int,
    ) : ConversationTimelineItem {
        override val stableKey = message.id?.let { "message:$it" } ?: "temporary-message-$chronologicalIndex"
    }

    data class PersistedGroup(
        val group: ConversationMessageGroup,
    ) : ConversationTimelineItem {
        override val stableKey = group.stableKey
    }

    data object OlderHistoryLoading : ConversationTimelineItem {
        override val stableKey = "loading-older"
    }

    data object Empty : ConversationTimelineItem {
        override val stableKey = "conversation-empty"
    }
}

internal fun buildConversationTimeline(
    history: List<Message>,
    pendingUserMessage: String?,
    streamingContent: String,
    showWaitingIndicator: Boolean,
    showOlderHistoryLoading: Boolean,
    includeInlineComposer: Boolean,
): List<ConversationTimelineItem> =
    buildList {
        if (includeInlineComposer) add(ConversationTimelineItem.InlineComposer)
        if (showWaitingIndicator) add(ConversationTimelineItem.Waiting)
        if (streamingContent.isNotEmpty()) add(ConversationTimelineItem.Streaming(streamingContent))
        if (pendingUserMessage != null) add(ConversationTimelineItem.PendingUser(pendingUserMessage))
        val persisted = history.indices.reversed().map { index -> ConversationTimelineItem.Persisted(history[index], index) }
        groupConsecutiveConversationMessages(persisted).forEach { group -> add(ConversationTimelineItem.PersistedGroup(group)) }
        if (showOlderHistoryLoading) add(ConversationTimelineItem.OlderHistoryLoading)
        if (history.isEmpty() && pendingUserMessage == null && streamingContent.isEmpty() && !showWaitingIndicator) {
            add(ConversationTimelineItem.Empty)
        }
    }

internal fun LazyListScope.ConversationTimeline(
    items: List<ConversationTimelineItem>,
    sending: Boolean,
    deletingMessageIds: Set<String>,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    sendContent: (String) -> Unit,
    inlineComposer: @Composable () -> Unit = {},
) {
    itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
        when (item) {
            ConversationTimelineItem.InlineComposer -> inlineComposer()
            ConversationTimelineItem.Waiting -> ConversationWaitingIndicator()
            is ConversationTimelineItem.Streaming -> StreamingTimelineRow(item.content, onStatusChange)
            is ConversationTimelineItem.PendingUser -> PendingTimelineRow(item.content, onStatusChange)
            is ConversationTimelineItem.PersistedGroup ->
                PersistedTimelineGroup(
                    item = item,
                    olderItems = items.drop(index + 1),
                    sending = sending,
                    deletingMessageIds = deletingMessageIds,
                    onDeleteMessage = onDeleteMessage,
                    onStatusChange = onStatusChange,
                    onEditMessage = onEditMessage,
                    sendContent = sendContent,
                )
            is ConversationTimelineItem.Persisted ->
                PersistedTimelineGroup(
                    item = ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(listOf(item))),
                    olderItems = items.drop(index + 1),
                    sending = sending,
                    deletingMessageIds = deletingMessageIds,
                    onDeleteMessage = onDeleteMessage,
                    onStatusChange = onStatusChange,
                    onEditMessage = onEditMessage,
                    sendContent = sendContent,
                )
            ConversationTimelineItem.OlderHistoryLoading -> OlderHistoryLoadingIndicator()
            ConversationTimelineItem.Empty ->
                PanelEmptyState(
                    title = "No conversation yet",
                    body = "Send a message to start the main agent session.",
                )
        }
    }
}

/** Renders conversation history messages in a [LazyListScope]. */
internal fun LazyListScope.ConversationMessageList(
    history: List<Message>,
    sending: Boolean,
    inFlight: InFlightStateHolder,
    pendingUserMessage: String?,
    streamingContent: String,
    deletingMessageIds: Set<String>,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    sendContent: (String) -> Unit,
    showWaitingIndicator: Boolean = inFlight.state.value.totalActive > 0 && streamingContent.isEmpty(),
) {
    ConversationTimeline(
        items =
            buildConversationTimeline(
                history = history.reversed(),
                pendingUserMessage = pendingUserMessage,
                streamingContent = streamingContent,
                showWaitingIndicator = showWaitingIndicator,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            ),
        sending = sending,
        deletingMessageIds = deletingMessageIds,
        onDeleteMessage = onDeleteMessage,
        onStatusChange = onStatusChange,
        onEditMessage = onEditMessage,
        sendContent = sendContent,
    )
}

@Composable
private fun StreamingTimelineRow(
    content: String,
    onStatusChange: (String) -> Unit,
) {
    TransientConversationMessageGroupRow(
        message = Message("assistant", content),
        isStreaming = true,
        onCopied = { onStatusChange("Copied assistant message") },
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun PendingTimelineRow(
    content: String,
    onStatusChange: (String) -> Unit,
) {
    TransientConversationMessageGroupRow(
        message = Message("user", content),
        isStreaming = false,
        onCopied = { onStatusChange("Copied user message") },
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun PersistedTimelineGroup(
    item: ConversationTimelineItem.PersistedGroup,
    olderItems: List<ConversationTimelineItem>,
    sending: Boolean,
    deletingMessageIds: Set<String>,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    sendContent: (String) -> Unit,
) {
    val group = item.group
    val message = group.messages.first().message
    val topPadding = 10.dp
    if (message.role == "user" || message.role == "assistant") {
        ConversationMessageGroupRow(
            group = group,
            sending = sending,
            deletingMessageIds = deletingMessageIds,
            onDeleteMessage = onDeleteMessage,
            onStatusChange = onStatusChange,
            onEditMessage = onEditMessage,
            onRetry = {
                val previous = olderItems.persistedMessages().firstOrNull { older -> older.message.role == "user" }
                if (previous == null) {
                    onStatusChange("No previous user message to retry")
                } else {
                    onStatusChange("Retrying previous user message...")
                    sendContent(previous.message.content)
                }
            },
            modifier = Modifier.padding(top = topPadding),
        )
        return
    }
    val onDelete: () -> Unit = {
        val messageId = message.id
        if (messageId != null) onDeleteMessage(messageId)
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
        "system" -> SystemMessageRow(message, Modifier.padding(top = topPadding))
        "sub_agent" ->
            SubAgentTimelineRow(
                message = message,
                deletingMessageIds = deletingMessageIds,
                onDelete = onDelete,
                onStatusChange = onStatusChange,
                modifier = Modifier.padding(top = topPadding),
            )
        else ->
            MessageRow(
                message = message,
                isStreamingPlaceholder = false,
                isStreaming = false,
                canRetry = message.role == "assistant" && !sending,
                canEdit = message.role == "user" && !sending,
                canDelete = message.id != null && message.role != "system" && message.id !in deletingMessageIds,
                isDeleting = message.id in deletingMessageIds,
                onCopied = { onStatusChange("Copied ${message.role} message") },
                onRetry = {
                    val previous =
                        olderItems
                            .persistedMessages()
                            .firstOrNull { it.message.role == "user" }
                    if (previous == null) {
                        onStatusChange("No previous user message to retry")
                    } else {
                        onStatusChange("Retrying previous user message...")
                        sendContent(previous.message.content)
                    }
                },
                onEdit = { onEditMessage(message.id) },
                onDelete = onDelete,
                modifier = Modifier.padding(top = topPadding),
            )
    }
}

private fun List<ConversationTimelineItem>.persistedMessages(): List<ConversationTimelineItem.Persisted> =
    filterIsInstance<ConversationTimelineItem.PersistedGroup>().flatMap { it.group.messages }

/** Renders the conversation waiting indicator from the canonical in-flight state. */
@Composable
internal fun ConversationWaitingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Thinking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PulsingDots()
    }
}
