@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
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

private val ConversationAuthorColumnWidth = 64.dp

@Composable
internal fun ConversationMessageGroupRow(
    group: ConversationMessageGroup,
    sending: Boolean,
    deletingMessageIds: Set<String>,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleMessages = group.messages.filterNot { it.message.id in deletingMessageIds }
    AnimatedVisibility(
        visible = visibleMessages.isNotEmpty(),
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        PanelContentCard(backgroundColor = groupBackground(group.role)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ConversationAuthorColumn(group.role)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleMessages.asReversed().forEach { item ->
                        key(item.stableKey) {
                            ConversationMessageGroupContent(
                                item = item,
                                sending = sending,
                                onDeleteMessage = onDeleteMessage,
                                onStatusChange = onStatusChange,
                                onEditMessage = onEditMessage,
                                onRetry = onRetry,
                            )
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
    modifier: Modifier = Modifier,
) {
    PanelContentCard(modifier = modifier, backgroundColor = groupBackground(message.role)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ConversationAuthorColumn(message.role)
            Column(modifier = Modifier.weight(1f)) {
                ConversationMessageContent(message, isStreamingPlaceholder = false, isStreaming = isStreaming)
            }
        }
    }
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(28.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isUser) Icons.Filled.Person else Icons.Filled.SmartToy,
                contentDescription = "$label avatar",
                tint = onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConversationMessageGroupContent(
    item: ConversationTimelineItem.Persisted,
    sending: Boolean,
    onDeleteMessage: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onEditMessage: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    val message = item.message
    ConversationMessageContent(message, isStreamingPlaceholder = false, isStreaming = false)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
        @Suppress("DEPRECATION")
        val clipboard = LocalClipboardManager.current
        ActionIconButton(
            icon = Icons.Filled.ContentCopy,
            description = "Copy ${message.role} message",
            modifier = Modifier.size(24.dp).alpha(0.6f),
            onClick = {
                clipboard.setText(AnnotatedString(message.content))
                onStatusChange("Copied ${message.role} message")
            },
        )
        if (message.role == "user" && !sending) {
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Edit user message",
                modifier = Modifier.size(24.dp).alpha(0.6f),
                onClick = { onEditMessage(message.id) },
            )
        }
        message.id?.let { messageId ->
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Delete ${message.role} message",
                modifier = Modifier.size(24.dp).alpha(0.6f),
                onClick = { onDeleteMessage(messageId) },
            )
        }
        if (message.role == "assistant" && !sending) {
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Retry from previous user message",
                modifier = Modifier.size(24.dp).alpha(0.6f),
                onClick = onRetry,
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
