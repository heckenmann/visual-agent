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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

@Composable
internal fun MessageRow(
    message: Message,
    isStreamingPlaceholder: Boolean,
    isStreaming: Boolean,
    canRetry: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    isDeleting: Boolean,
    onCopied: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == "user"
    val accent = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val background =
        if (isUser) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    AnimatedVisibility(
        visible = !isDeleting,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(background, shape = MaterialTheme.shapes.medium).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
                Text(
                    text = if (isUser) "You" else "Assistant",
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ActionIconButton(
                    icon = Icons.Filled.ContentCopy,
                    description = "Copy ${message.role} message",
                    modifier = Modifier.size(24.dp).alpha(0.6f),
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        onCopied()
                    },
                )
                if (canEdit) {
                    ActionIconButton(
                        icon = Icons.Filled.Edit,
                        description = "Edit ${message.role} message",
                        modifier = Modifier.size(24.dp).alpha(0.6f),
                        onClick = onEdit,
                    )
                }
                if (canDelete) {
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Delete ${message.role} message",
                        modifier = Modifier.size(24.dp).alpha(0.6f),
                        onClick = onDelete,
                    )
                }
                if (canRetry) {
                    ActionIconButton(
                        icon = Icons.Filled.Refresh,
                        description = "Retry from previous user message",
                        modifier = Modifier.size(24.dp).alpha(0.6f),
                        onClick = onRetry,
                    )
                }
            }
            ConversationMessageContent(message, isStreamingPlaceholder, isStreaming)
        }
    }
}

@Composable
internal fun ConversationMessageContent(
    message: Message,
    isStreamingPlaceholder: Boolean,
    isStreaming: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isStreamingPlaceholder) {
            Text(
                text = "Thinking…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (isStreaming) {
            SelectionContainer {
                StreamingText(text = message.content, animate = false) { displayedText ->
                    Text(
                        text = displayedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            SelectionContainer { ComposeMarkdown(message.content) }
        }
        ConversationImageAttachments(message.images.orEmpty())
    }
}

@Composable
internal fun SystemMessageRow(
    message: Message,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message.content,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
internal fun EditMessageModal(
    content: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var edited by remember { mutableStateOf(content) }
    ComposeContentModal(title = "Edit message") { dismiss ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = edited,
                onValueChange = { edited = it },
                label = { Text("Content") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                ActionIconButton(icon = Icons.Filled.Close, description = "Cancel edit", onClick = dismiss)
                ActionIconButton(
                    icon = Icons.Filled.Done,
                    description = "Save message",
                    enabled = edited.isNotBlank(),
                    onClick = { onSave(edited) },
                )
            }
        }
    }
}

internal const val DELETE_ANIMATION_DURATION_MS = 220
