@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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

internal data class ConversationMessageGroup(
    val messages: List<ConversationTimelineItem.Persisted>,
) {
    init {
        require(messages.isNotEmpty())
    }

    val role: String = messages.first().message.role
    val stableKey: String = messages.first().stableKey
}

internal fun groupConsecutiveConversationMessages(messages: List<ConversationTimelineItem.Persisted>): List<ConversationMessageGroup> {
    val groups = mutableListOf<MutableList<ConversationTimelineItem.Persisted>>()
    messages.forEach { message ->
        val previous = groups.lastOrNull()
        if (previous != null && previous.first().message.role == message.message.role && message.message.role.isConversationalRole()) {
            previous += message
        } else {
            groups += mutableListOf(message)
        }
    }
    return groups.map(::ConversationMessageGroup)
}

private fun String.isConversationalRole(): Boolean = this == "user" || this == "assistant"

private val ConversationAuthorColumnWidth = 48.dp

@Composable
internal fun ConversationMessageGroupRow(
    group: ConversationMessageGroup,
    sending: Boolean,
    deletingMessageIds: Set<String>,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    onRetry: () -> Unit,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visibleState =
            rememberConversationMessageVisibility(
                isVisible = group.messages.any { it.message.id !in deletingMessageIds },
            ),
        enter = conversationMessageEnterTransition(),
        exit = conversationMessageDeleteTransition(),
        modifier = modifier.fillMaxWidth(),
    ) {
        PanelContentCard(backgroundColor = groupBackground(group.role)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ConversationAuthorColumn(group.role)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.messages.asReversed().forEach { item ->
                        key(item.stableKey) {
                            AnimatedVisibility(
                                visibleState =
                                    rememberConversationMessageVisibility(
                                        isVisible = item.message.id !in deletingMessageIds,
                                    ),
                                enter = conversationMessageEnterTransition(),
                                exit = conversationMessageDeleteTransition(),
                            ) {
                                ConversationMessageGroupContent(
                                    item = item,
                                    sending = sending,
                                    onDeleteMessage = onDeleteMessage,
                                    onStatusChange = onStatusChange,
                                    onEditMessage = onEditMessage,
                                    onRetry = onRetry,
                                    isStreaming = isStreaming,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransientConversationMessageGroupRow(
    message: Message,
    isStreaming: Boolean,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConversationMessageGroupRow(
        group = ConversationMessageGroup(listOf(ConversationTimelineItem.Persisted(message, 0))),
        sending = true,
        deletingMessageIds = emptySet(),
        onDeleteMessage = {},
        onStatusChange = { onCopied() },
        onEditMessage = {},
        onRetry = {},
        isStreaming = isStreaming,
        modifier = modifier,
    )
}

@Composable
private fun ConversationAuthorColumn(role: String) {
    val isUser = role == "user"
    val accent = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val onAccent = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
    val label = if (isUser) "You" else "Assistant"
    Column(
        modifier = Modifier.width(ConversationAuthorColumnWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(modifier = Modifier.size(24.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isUser) Icons.Filled.Person else Icons.Filled.SmartToy,
                contentDescription = "$label avatar",
                tint = onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ConversationMessageGroupContent(
    item: ConversationTimelineItem.Persisted,
    sending: Boolean,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    onRetry: () -> Unit,
    isStreaming: Boolean,
) {
    val message = item.message
    var hovered by remember(message.id) { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ConversationMessageContent(
            message = message,
            isStreamingPlaceholder = false,
            isStreaming = isStreaming,
            modifier = Modifier.weight(1f),
        )
        if (!isStreaming) {
            conversationMessageActionMenu(
                message = message,
                canEdit = message.role == "user" && !sending,
                canDelete = message.id != null,
                canRetry = message.role == "assistant" && !sending,
                onEdit = { onEditMessage(message.id) },
                onDelete = { message.id?.let(onDeleteMessage) },
                onRetry = onRetry,
                onCopied = { onStatusChange("Copied ${message.role} message") },
                timestamp = message.createdAtEpochMillis,
                showTimestamp = hovered,
            )
        }
    }
}

@Composable
private fun groupBackground(role: String) =
    if (role == "user") {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

@Composable
internal fun SubAgentTimelineRow(
    message: Message,
    deletingMessageIds: Set<String>,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = parseSubAgentMetadata(message.metadata)
    if (!shouldUseSubAgentSummary(metadata)) {
        MessageRow(
            message = message,
            isStreamingPlaceholder = false,
            isStreaming = false,
            canRetry = false,
            canEdit = false,
            canDelete = message.id != null && message.id !in deletingMessageIds,
            isDeleting = message.id in deletingMessageIds,
            onCopied = { onStatusChange("Copied sub-agent message") },
            onRetry = {},
            onEdit = {},
            onDelete = onDelete,
            modifier = modifier,
        )
    } else {
        SubAgentMessageRow(
            message = message,
            isDeleting = message.id in deletingMessageIds,
            isRunning = false,
            onDelete = onDelete,
            modifier = modifier,
        )
    }
}

/** Returns whether complete metadata is available for the specialist status row. */
internal fun shouldUseSubAgentSummary(metadata: ParsedSubAgentMetadata): Boolean =
    !metadata.agentName.isNullOrBlank() && metadata.hasCompletionStatus
