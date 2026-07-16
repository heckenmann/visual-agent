@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message

@Composable
internal fun MessageRow(
    message: Message,
    isStreamingPlaceholder: Boolean,
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
    val backgroundColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        PanelContentCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = backgroundColor,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.role.uppercase(),
                    color = if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ActionIconButton(
                    icon = Icons.Filled.ContentCopy,
                    description = "Copy ${message.role} message",
                    modifier = Modifier.size(28.dp),
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        onCopied()
                    },
                )
                if (canEdit) {
                    ActionIconButton(
                        icon = Icons.Filled.Edit,
                        description = "Edit ${message.role} message",
                        modifier = Modifier.size(28.dp),
                        onClick = onEdit,
                    )
                }
                if (canDelete) {
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Delete ${message.role} message",
                        modifier = Modifier.size(28.dp),
                        onClick = onDelete,
                    )
                }
                if (canRetry) {
                    ActionIconButton(
                        icon = Icons.Filled.Refresh,
                        description = "Retry from previous user message",
                        modifier = Modifier.size(28.dp),
                        onClick = onRetry,
                    )
                }
            }
            if (isStreamingPlaceholder) {
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                ComposeMarkdown(message.content)
            }
        }
    }
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
