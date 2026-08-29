package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.heckenmann.visualagent.ui.components.ActionIconButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/** Renders compact contextual actions and an optional hover timestamp for one message. */
@Composable
internal fun conversationMessageActionMenu(
    message: Message,
    canEdit: Boolean,
    canDelete: Boolean,
    canRetry: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onCopied: () -> Unit,
    timestamp: Long? = null,
    showTimestamp: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConversationCopyAction(message = message, onCopied = onCopied)
            ActionIconButton(
                icon = Icons.Filled.MoreVert,
                description = "Message actions",
                modifier = Modifier.size(24.dp).alpha(0.6f),
                onClick = { expanded = true },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            conversationActionMenuItems(
                message = message,
                canEdit = canEdit,
                canDelete = canDelete,
                canRetry = canRetry,
                onEdit = onEdit,
                onDelete = onDelete,
                onRetry = onRetry,
                dismiss = { expanded = false },
            )
        }
        if (showTimestamp && timestamp != null) {
            conversationTimestampPopup(timestamp)
        }
    }
}

/** Displays a timestamp outside the message layout without intercepting pointer input. */
@Composable
private fun conversationTimestampPopup(timestamp: Long) {
    val verticalOffset = with(LocalDensity.current) { -32.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, verticalOffset),
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = MaterialTheme.shapes.extraSmall,
            tonalElevation = 2.dp,
        ) {
            Text(
                text = formatConversationTimestamp(timestamp),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun conversationActionMenuItems(
    message: Message,
    canEdit: Boolean,
    canDelete: Boolean,
    canRetry: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    dismiss: () -> Unit,
) {
    if (canEdit) conversationActionMenuItem("Edit", Icons.Filled.Edit, onEdit, dismiss)
    if (canRetry) conversationActionMenuItem("Retry", Icons.Filled.Refresh, onRetry, dismiss)
    if (canDelete) conversationActionMenuItem("Delete", Icons.Filled.Delete, onDelete, dismiss)
}

@Composable
private fun conversationActionMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            dismiss()
            onClick()
        },
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

private fun formatConversationTimestamp(epochMillis: Long): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
