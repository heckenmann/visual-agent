@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.ui.components.PanelContentCard
import de.heckenmann.visualagent.ui.components.labelizeEnumName
import de.heckenmann.visualagent.ui.todo.TODO_RESPONSE_PREVIEW_MAX_LINES
import de.heckenmann.visualagent.ui.todo.TodoResponseState
import de.heckenmann.visualagent.ui.todo.TodoWorkingIndicator
import de.heckenmann.visualagent.ui.todo.todoResponseTail

/** Renders a compact todo card anchored in the conversation timeline. */
@Composable
internal fun ConversationTodoCard(
    todo: TodoItem,
    responseState: TodoResponseState,
    deleted: Boolean,
    onOpenResponse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = if (deleted) "Unavailable" else todo.status.name.labelizeEnumName()
    val statusColor = if (deleted) MaterialTheme.colorScheme.error else todoStatusColor(todo.status)
    PanelContentCard(modifier = modifier.fillMaxWidth(), backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (deleted) Icons.Filled.DeleteOutline else todoStatusIcon(todo.status),
                    contentDescription = status,
                    tint = statusColor,
                )
                Text("Todo", style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor)
                todo.assignedAgentId?.let { agentId ->
                    Text("· $agentId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(todo.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (!deleted && todo.status == TodoState.IN_PROGRESS) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TodoWorkingIndicator()
                    Text("Working…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ConversationTodoResponsePreview(responseState = responseState, onOpen = onOpenResponse)
        }
    }
}

/** Renders the newest todo output at the bottom while older lines scroll upward. */
@Composable
private fun ConversationTodoResponsePreview(
    responseState: TodoResponseState,
    onOpen: () -> Unit,
) {
    if (responseState.text.isBlank()) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .semantics { contentDescription = "Open full todo response" },
    ) {
        Text(
            text = todoResponseTail(responseState.text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = TODO_RESPONSE_PREVIEW_MAX_LINES,
        )
    }
}

@Composable
private fun todoStatusColor(status: TodoState) =
    when (status) {
        TodoState.PENDING -> MaterialTheme.colorScheme.tertiary
        TodoState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        TodoState.COMPLETED -> MaterialTheme.colorScheme.secondary
        TodoState.CANCELLED -> MaterialTheme.colorScheme.error
    }

private fun todoStatusIcon(status: TodoState) =
    when (status) {
        TodoState.PENDING -> Icons.Filled.Schedule
        TodoState.IN_PROGRESS -> Icons.Filled.PlayArrow
        TodoState.COMPLETED -> Icons.Filled.CheckCircle
        TodoState.CANCELLED -> Icons.Filled.ErrorOutline
    }
