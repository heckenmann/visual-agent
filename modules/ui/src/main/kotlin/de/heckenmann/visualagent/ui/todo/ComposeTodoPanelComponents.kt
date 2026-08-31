@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.AgentSummary
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoState
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
import sh.calvin.reorderable.ReorderableColumnScope
import sh.calvin.reorderable.ReorderableItem

@Composable
internal fun ReorderableColumnScope.TodoRow(
    todo: TodoItem,
    isNext: Boolean,
    isDragging: Boolean,
    responseState: TodoResponseState,
    currentTodo: () -> TodoItem? = { todo },
    todoPort: TodoPort,
    modalRequester: ComposeModalRequester,
    refresh: () -> Unit,
) {
    val alpha = if (isDragging) 0.7f else 1f
    val containerColor =
        if (isNext) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
        }
    val agents = remember { todoPort.agents() }
    val agentName = todo.assignedAgentId?.let { id -> agents.firstOrNull { it.id == id }?.name }
    ReorderableItem {
        PanelContentCard(
            modifier = Modifier.fillMaxWidth().alpha(alpha).animateContentSize(),
            backgroundColor = containerColor,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TodoDragHandle(modifier = Modifier.draggableHandle())
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        todo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TodoMetaLine(todo = todo, isNext = isNext, agentName = agentName)
                    TodoStreamingResponse(
                        visible = todo.status == TodoState.IN_PROGRESS,
                        working = todo.status == TodoState.IN_PROGRESS,
                        responseState = responseState,
                        modalRequester = modalRequester,
                        todo = todo,
                        currentTodo = currentTodo,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionIconButton(
                        icon = Icons.Filled.Edit,
                        description = "Edit todo",
                        onClick = {
                            modalRequester.request(
                                ComposeContentModal(title = "Edit todo") { dismiss ->
                                    TodoEditor(
                                        todo = todo,
                                        agents = todoPort.agents(),
                                        onCancel = dismiss,
                                        onSave = { updatedDescription, updatedStatus, updatedAgentId ->
                                            todoPort.updateDescription(todo.id, updatedDescription)
                                            todoPort.updateStatus(todo.id, updatedStatus)
                                            todoPort.updateAssignedAgent(todo.id, updatedAgentId)
                                            refresh()
                                            dismiss()
                                        },
                                    )
                                },
                            )
                        },
                    )
                    ActionIconButton(
                        icon = Icons.Filled.PlayArrow,
                        description = "Start todo",
                        enabled = todo.status == TodoState.PENDING || todo.status == TodoState.CANCELLED,
                        onClick = {
                            todoPort.start(todo.id)
                            refresh()
                        },
                    )
                    ActionIconButton(
                        icon = Icons.Filled.Stop,
                        description = "Stop todo",
                        enabled = todo.status == TodoState.PENDING || todo.status == TodoState.IN_PROGRESS,
                        onClick = {
                            todoPort.stop(todo.id)
                            refresh()
                        },
                    )
                    ActionIconButton(
                        icon = Icons.Filled.Done,
                        description = "Complete todo",
                        enabled = todo.status != TodoState.COMPLETED && todo.status != TodoState.CANCELLED,
                        onClick = {
                            todoPort.updateStatus(todo.id, TodoState.COMPLETED)
                            refresh()
                        },
                    )
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Delete todo",
                        onClick = {
                            modalRequester.requestConfirmation(
                                ComposeConfirmationModal(
                                    title = "Delete todo?",
                                    message = "Delete '${todo.description}' from the persisted todo list.",
                                    confirmDescription = "Delete todo",
                                ) {
                                    todoPort.remove(todo.id)
                                    refresh()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TodoMetaLine(
    todo: TodoItem,
    isNext: Boolean,
    agentName: String?,
) {
    val statusColor =
        when (todo.status) {
            TodoState.PENDING -> MaterialTheme.colorScheme.tertiary
            TodoState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
            TodoState.COMPLETED -> MaterialTheme.colorScheme.secondary
            TodoState.CANCELLED -> MaterialTheme.colorScheme.error
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isNext) {
            Text(
                text = "NEXT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        when (todo.status) {
            TodoState.PENDING ->
                Icon(Icons.Filled.Schedule, contentDescription = "Pending", tint = statusColor, modifier = Modifier.size(14.dp))
            TodoState.IN_PROGRESS ->
                Icon(Icons.Filled.PlayArrow, contentDescription = "In progress", tint = statusColor, modifier = Modifier.size(14.dp))
            TodoState.COMPLETED ->
                Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = statusColor, modifier = Modifier.size(14.dp))
            TodoState.CANCELLED ->
                Icon(Icons.Filled.Cancel, contentDescription = "Cancelled", tint = statusColor, modifier = Modifier.size(14.dp))
        }
        Text(
            text = todo.status.name.labelizeEnumName(),
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
        )
        if (todo.assignedAgentId != null) {
            Text(
                text = "· ${agentName ?: todo.assignedAgentId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TodoDragHandle(modifier: Modifier = Modifier) {
    val gripColor = MaterialTheme.colorScheme.outline
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(18.dp),
    ) {
        repeat(4) {
            Box(
                modifier =
                    Modifier
                        .width(12.dp)
                        .height(2.dp)
                        .background(gripColor, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
internal fun TodoEditor(
    todo: TodoItem,
    agents: List<AgentSummary>,
    onCancel: () -> Unit,
    onSave: (String, TodoState, String?) -> Unit,
) {
    var description by remember(todo.id) { mutableStateOf(todo.description) }
    var status by remember(todo.id) { mutableStateOf(todo.status) }
    var assignedAgentId by remember(todo.id) { mutableStateOf(todo.assignedAgentId ?: UNASSIGNED_AGENT_ID) }
    val agentOptions =
        listOf(PanelSelectOption(UNASSIGNED_AGENT_ID, "Unassigned")) +
            agents.map { PanelSelectOption(it.id, it.name) }
    modalDialogLayout(
        body = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            PanelDropdownField(
                label = "Status",
                selectedValue = status.name,
                options = TodoState.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { status = TodoState.valueOf(it) },
            )
            PanelDropdownField(
                label = "Assigned agent",
                selectedValue = assignedAgentId,
                options = agentOptions,
                onSelected = { assignedAgentId = it },
            )
        },
        footer = {
            modalSecondaryButton(label = "Cancel", onClick = onCancel)
            modalSaveButton(
                label = "Save todo",
                enabled = description.isNotBlank(),
                onClick = {
                    val selectedAgent = assignedAgentId.takeIf { it != UNASSIGNED_AGENT_ID }
                    onSave(description.trim(), status, selectedAgent)
                },
            )
        },
    )
}

internal const val UNASSIGNED_AGENT_ID = "__unassigned__"
